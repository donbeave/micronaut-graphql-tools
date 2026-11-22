/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.execution

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.schema.DataFetchingEnvironment
import io.micronaut.configuration.graphql.DefaultGraphQLExecutionInputCustomizer
import io.micronaut.configuration.graphql.DefaultGraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLExecutionInputCustomizer
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Order
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.internal.BeanSelectionLogger
import io.micronaut.graphql.tools.internal.execution.ExecutionContextAvailability
import io.micronaut.graphql.tools.internal.execution.ExecutionState
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.graphql.tools.execution.GraphQLTransport
import io.micronaut.graphql.tools.internal.execution.LibraryGraphQLInvocation
import io.micronaut.http.HttpRequest
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.util.context.Context
import spock.lang.IgnoreIf
import spock.lang.Requires as SpockRequires

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ExecutorService

/** The library's GraphQLInvocation: the executor hop, the call order, the step-aside rules and the startup failures. */
class LibraryInvocationSpec extends AbstractTest {

    static final String SPEC_NAME = 'LibraryInvocationSpec'

    static final List<String> CALLS = Collections.synchronizedList([])
    static final List<Thread> RESOLVER_THREADS = Collections.synchronizedList([])
    static final List<Object> SEEN = Collections.synchronizedList([])
    static final List<Object> FETCH_MARKERS = Collections.synchronizedList([])
    static final List<Thread> CUSTOMIZER_THREADS = Collections.synchronizedList([])

    @Language('GraphQL')
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
  child: Child
}

type Child {
  name: String
}
"""

    void setup() {
        ProbeCustomizer.reset()
        CALLS.clear()
        RESOLVER_THREADS.clear()
        SEEN.clear()
        FETCH_MARKERS.clear()
        CUSTOMIZER_THREADS.clear()
    }

    private LibraryGraphQLInvocation invocation() {
        applicationContext.getBean(LibraryGraphQLInvocation)
    }

    private ExecutionResult run(String query = '{ hello child { name } }') {
        Mono.from(applicationContext.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, null, [:]), null, null)).block()
    }

    void "the library replaces the transport's default invocation"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        expect:
        applicationContext.getBean(GraphQLInvocation) instanceof LibraryGraphQLInvocation
        applicationContext.getBeansOfType(GraphQLInvocation).size() == 1
        applicationContext.getBean(LibraryGraphQLInvocation).executorName() == 'blocking'
    }

    void "the default executor runs the operation off the calling thread, with one thread change"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = run()

        then:
        result.errors.isEmpty()
        result.data == [hello: 'world', child: [name: 'kid']]
        RESOLVER_THREADS.size() == 2
        RESOLVER_THREADS.every { it != Thread.currentThread() }
        RESOLVER_THREADS.toSet().size() == 1
    }

    void "none runs the operation on the calling thread"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.executor': 'none'])

        when:
        def result = run()

        then:
        result.errors.isEmpty()
        RESOLVER_THREADS.size() == 2
        RESOLVER_THREADS.every { it == Thread.currentThread() }
    }

    void "a named executor bean runs the operation, and the resolvers run on it after one hop from the customizer"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.recording', ['micronaut.graphql.tools.execution.executor': 'counting',
                                         'micronaut.executors.counting.type'          : 'fixed',
                                         'micronaut.executors.counting.nThreads'      : 1])

        when:
        def result = run()

        then:
        result.errors.isEmpty()
        RESOLVER_THREADS*.name.every { it.startsWith('counting') }
        RESOLVER_THREADS.toSet().size() == 1
        CUSTOMIZER_THREADS == [Thread.currentThread()]
        RESOLVER_THREADS[0] != Thread.currentThread()
    }

    void "a customizer that extends the transport's default one still runs"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.subclass', ['micronaut.graphql.tools.execution.executor': 'io'])

        when:
        run()

        then:
        CUSTOMIZER_THREADS.size() == 1
    }

    void "the customizer runs on the subscribing thread and before graphql-java, and the state survives a fresh input"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.customizer', ['micronaut.graphql.tools.execution.executor': 'io'])

        when:
        def result = run()

        then: 'the customizer replaced the input and its query ran'
        result.errors.isEmpty()
        result.data == [hello: 'world', child: [name: 'kid']]
        CALLS == ['customizer', 'fetch']
        and: 'it saw the base input with the state in its context, on the subscribing thread'
        SEEN[0] instanceof ExecutionState
        SEEN[0].transport() == GraphQLTransport.HTTP
        SEEN[1] == Thread.currentThread()
        and: 'the resolver found the state in the context of the fresh input'
        SEEN[2] instanceof ExecutionState
        SEEN[2].is(SEEN[0])
    }

    void "a customizer sees the Reactor context of the subscriber"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.probe', ['micronaut.graphql.tools.execution.executor': 'io'])

        when:
        def result = Mono.from(invocation().invoke(new GraphQLInvocationData('{ hello }', null, [:]), null, null))
                .contextWrite(Context.of('probe-key', 'from-subscriber')).block()

        then:
        result.errors.isEmpty()
        SEEN[0] == 'from-subscriber'
    }

    void "cancelling the returned publisher cancels the customizer subscription"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.probe', ['micronaut.graphql.tools.execution.executor': 'io'])
        ProbeCustomizer.NEVER = true

        when:
        def subscription = Mono.from(invocation().invoke(new GraphQLInvocationData('{ hello }', null, [:]), null, null)).subscribe()
        def subscribed = ProbeCustomizer.SUBSCRIBED.get()
        subscription.dispose()

        then:
        subscribed == 1
        ProbeCustomizer.CANCELLED.get() == 1
        CALLS.isEmpty()

        cleanup:
        ProbeCustomizer.NEVER = false
    }

    void "a customizer that never completes is disposed when the timeout fires before its subscription is stored"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.window', ['micronaut.graphql.tools.execution.timeout': '50ms'])

        when:
        def result = Mono.from(invocation().invoke(new GraphQLInvocationData('{ hello }', null, [:]), null, null)).block(java.time.Duration.ofSeconds(10))

        then:
        WindowCustomizer.TIMER_RAN.await(0, TimeUnit.SECONDS)
        result.errors[0].extensions.code == 'EXECUTION_TIMEOUT'
        WindowCustomizer.CANCELLED.await(5, TimeUnit.SECONDS)
        CALLS.isEmpty()
    }

    void "a returned publisher that is never subscribed starts no execution"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.probe', ['micronaut.graphql.tools.execution.executor': 'io'])

        when:
        invocation().invoke(new GraphQLInvocationData('{ hello }', null, [:]), null, null)
        Thread.sleep(200)

        then:
        ProbeCustomizer.CALLED.get() == 0
        CALLS.isEmpty()
    }

    void "the propagated context of the caller is visible in the resolvers"() {
        given:
        startContext(SCHEMA, SPEC_NAME)
        def element = new MarkerElement('request-1')
        ExecutionResult result

        when:
        def scope = PropagatedContext.getOrEmpty().plus(element).propagate()
        try {
            result = run()
        } finally {
            scope.close()
        }

        then:
        result.errors.isEmpty()
        SEEN.contains(element)
    }

    void "the propagated context is captured when invoke is called, so a subscriber on another thread still sees it"() {
        given:
        startContext(SCHEMA, SPEC_NAME)
        def element = new MarkerElement('request-2')
        Publisher<ExecutionResult> publisher
        ExecutionResult result

        when: 'invoke is called with the context, and the publisher is subscribed later on a thread without it'
        def scope = PropagatedContext.getOrEmpty().plus(element).propagate()
        try {
            publisher = invocation().invoke(new GraphQLInvocationData('{ hello }', null, [:]), null, null)
        } finally {
            scope.close()
        }
        def pool = Executors.newSingleThreadExecutor()
        try {
            result = pool.submit({ Mono.from(publisher).block() } as java.util.concurrent.Callable<ExecutionResult>).get(10, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        then:
        PropagatedContext.getOrEmpty().findAll(MarkerElement).findFirst().isEmpty()
        result.errors.isEmpty()
        SEEN.contains(element)
    }

    void "the propagated context is captured when invoke is called, so a subscriber on another thread still sees it with a customizer and no executor"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.customizer', ['micronaut.graphql.tools.execution.executor': 'none'])
        def element = new MarkerElement('request-3')
        Publisher<ExecutionResult> publisher
        ExecutionResult result

        when: 'invoke is called with the context, and the publisher is subscribed later on a thread without it'
        def scope = PropagatedContext.getOrEmpty().plus(element).propagate()
        try {
            publisher = invocation().invoke(new GraphQLInvocationData('{ hello }', null, [:]), null, null)
        } finally {
            scope.close()
        }
        def pool = Executors.newSingleThreadExecutor()
        try {
            result = pool.submit({ Mono.from(publisher).block() } as java.util.concurrent.Callable<ExecutionResult>).get(10, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        then:
        PropagatedContext.getOrEmpty().findAll(MarkerElement).findFirst().isEmpty()
        result.errors.isEmpty()
        FETCH_MARKERS.contains(element)
    }

    void "an executor that rejects the hop gives one EXECUTION_REJECTED error with data null"() {
        given:
        def appender = captureWarnings()
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.executor': 'rejecting'])

        when:
        def result = run()

        then:
        result.data == null
        result.errors.size() == 1
        result.errors[0].extensions.code == 'EXECUTION_REJECTED'
        RESOLVER_THREADS.isEmpty()
        appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("'rejecting'") }

        cleanup:
        warningLogger().detachAppender(appender)
    }

    void "an executor that throws a runtime exception from execute gives a failed publisher, not a thrown exception"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.executor': 'throwing'])

        when:
        def publisher = applicationContext.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData('{ hello }', null, [:]), null, null)

        then:
        noExceptionThrown()

        when:
        Mono.from(publisher).block()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'execute failed'
        RESOLVER_THREADS.isEmpty()
    }

    void "cancelling the result of an execution that is still queued skips its hop task"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.executor': 'queued'])
        ExecutorService queued = applicationContext.getBean(ExecutorService, io.micronaut.inject.qualifiers.Qualifiers.byName('queued'))
        def release = new java.util.concurrent.CountDownLatch(1)
        queued.execute { release.await() }

        when: 'the operation waits behind the blocker and the client goes away'
        def future = Mono.from(applicationContext.getBean(LibraryGraphQLInvocation)
                .execute(new GraphQLInvocationData('{ hello }', null, [:]), GraphQLTransport.WEBSOCKET, null, null,
                        ConvertibleValues.empty())).toFuture()
        future.cancel(true)
        release.countDown()
        queued.shutdown()
        queued.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)

        then: 'no resolver ran'
        RESOLVER_THREADS.isEmpty()
        future.isCancelled()
    }

    void "an unknown executor fails the startup with unknown-executor and lists the executor beans"() {
        when:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.eager-init': true,
                                         'micronaut.graphql.tools.execution.executor': 'nowhere'])

        then:
        def e = thrown(BeanInstantiationException)
        def cause = causeOfType(e, SchemaBindingException)
        cause.getCode() == BindingCode.UNKNOWN_EXECUTOR
        cause.message.startsWith('[unknown-executor] ')
        cause.message.contains("'nowhere'")
        cause.message.contains('blocking')
        cause.message.contains('io')
    }

    void "scheduled is rejected as unknown-executor"() {
        when:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.eager-init': true,
                                         'micronaut.graphql.tools.execution.executor': 'scheduled'])

        then:
        def e = thrown(BeanInstantiationException)
        causeOfType(e, SchemaBindingException).getCode() == BindingCode.UNKNOWN_EXECUTOR
        causeOfType(e, SchemaBindingException).message.contains('scheduled')
    }

    @IgnoreIf({ Runtime.version().feature() >= 21 })
    void "virtual on JDK 17 fails the startup with unknown-executor and says so"() {
        when:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.eager-init': true,
                                         'micronaut.graphql.tools.execution.executor': 'virtual'])

        then:
        def e = thrown(BeanInstantiationException)
        def cause = causeOfType(e, SchemaBindingException)
        cause.getCode() == BindingCode.UNKNOWN_EXECUTOR
        cause.message.contains('JDK 21')
        cause.message.contains("'blocking'")
    }

    @SpockRequires({ Runtime.version().feature() >= 21 })
    void "virtual runs the operation on a virtual thread on JDK 21 and later"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.executor': 'virtual'])

        when:
        run()

        then:
        RESOLVER_THREADS.every { it.isVirtual() }
    }

    void "an application invocation with @Replaces wins, the library logs one INFO line and a context-bound resolver fails"() {
        given:
        def appender = captureInfo()

        when:
        startContext(SCHEMA, SPEC_NAME + '.replaces')

        then:
        !applicationContext.findBean(LibraryGraphQLInvocation).isPresent()
        applicationContext.getBean(GraphQLInvocation) instanceof ReplacingInvocation
        applicationContext.getBeansOfType(GraphQLInvocation).size() == 1
        !ExecutionContextAvailability.isAvailable(applicationContext)

        when: 'the startup logged the choice once'
        def lines = appender.list.findAll { it.formattedMessage.contains('does not create its GraphQLInvocation bean') }

        then:
        lines.size() == 1
        lines[0].level == Level.INFO
        lines[0].formattedMessage.contains('ReplacingInvocation')

        when:
        ExecutionContextAvailability.require(applicationContext, 'Query.book (parameter request of Books#book)', 'the HTTP request')

        then:
        def e = thrown(SchemaBindingException)
        e.getCode() == BindingCode.EXECUTION_CONTEXT_UNAVAILABLE
        e.message.startsWith('[execution-context-unavailable] Query.book')
        e.message.contains('ReplacingInvocation')

        cleanup:
        infoLogger().detachAppender(appender)
    }

    void "an application invocation that is @Primary wins as well"() {
        given:
        def appender = captureInfo()

        when:
        startContext(SCHEMA, SPEC_NAME + '.primary')

        then:
        !applicationContext.findBean(LibraryGraphQLInvocation).isPresent()
        applicationContext.getBean(GraphQLInvocation) instanceof PrimaryInvocation
        !ExecutionContextAvailability.isAvailable(applicationContext)

        then:
        appender.list.count { it.formattedMessage.contains('does not create its GraphQLInvocation bean') } == 1

        cleanup:
        infoLogger().detachAppender(appender)
    }

    void "an application @Primary factory method that returns DefaultGraphQLInvocation wins, with the INFO line"() {
        given:
        def appender = captureInfo()

        when:
        startContext(SCHEMA, SPEC_NAME + '.factory')

        then:
        !applicationContext.findBean(LibraryGraphQLInvocation).isPresent()
        applicationContext.getBean(GraphQLInvocation) instanceof DefaultGraphQLInvocation
        !ExecutionContextAvailability.isAvailable(applicationContext)

        then:
        appender.list.count { it.formattedMessage.contains('does not create its GraphQLInvocation bean') } == 1

        cleanup:
        infoLogger().detachAppender(appender)
    }

    void "with the library's invocation the execution context is available and nothing fails"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        expect:
        ExecutionContextAvailability.isAvailable(applicationContext)
        ExecutionContextAvailability.require(applicationContext, 'Query.hello', 'a DataLoader') == null
    }

    private static Throwable causeOfType(Throwable e, Class<? extends Throwable> type) {
        for (Throwable cause = e; cause != null; cause = cause.cause) {
            if (type.isInstance(cause)) {
                return cause
            }
        }
        throw new AssertionError("No cause of type ${type.simpleName} in ${e}")
    }

    private static Logger infoLogger() {
        (Logger) org.slf4j.LoggerFactory.getLogger(BeanSelectionLogger)
    }

    private static Logger warningLogger() {
        (Logger) org.slf4j.LoggerFactory.getLogger(LibraryGraphQLInvocation)
    }

    private static ListAppender<ILoggingEvent> captureInfo() {
        attach(infoLogger())
    }

    private static ListAppender<ILoggingEvent> captureWarnings() {
        attach(warningLogger())
    }

    private static ListAppender<ILoggingEvent> attach(Logger logger) {
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        appender
    }

    record MarkerElement(String id) implements PropagatedContextElement {
    }

    @Singleton
    @Requires(property = 'spec.name', pattern = 'LibraryInvocationSpec(\\.recording)?')
    @GraphQLRootResolver
    static class Query {
        String hello(DataFetchingEnvironment environment) {
            RESOLVER_THREADS << Thread.currentThread()
            SEEN << PropagatedContext.getOrEmpty().findAll(MarkerElement).findFirst().orElse(null)
            if (!CALLS.contains('customizer') && ExecutionState.of(environment.graphQlContext) != null) {
                SEEN << ExecutionState.of(environment.graphQlContext)
            }
            'world'
        }

        Child child() {
            RESOLVER_THREADS << Thread.currentThread()
            new Child()
        }
    }

    @GraphQLType
    static class Child {
        String name = 'kid'
    }

    @Singleton
    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(customizer|subclass|probe|window)')
    @GraphQLRootResolver
    static class CustomizedQuery {
        String hello(DataFetchingEnvironment environment) {
            CALLS << 'fetch'
            FETCH_MARKERS << PropagatedContext.getOrEmpty().findAll(MarkerElement).findFirst().orElse(null)
            SEEN << ExecutionState.of(environment.graphQlContext)
            'world'
        }

        Child child() {
            new Child()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.customizer')
    @Singleton
    static class RebuildingCustomizer implements GraphQLExecutionInputCustomizer {
        @Override
        Publisher<ExecutionInput> customize(ExecutionInput executionInput, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            CALLS << 'customizer'
            SEEN << ExecutionState.of(executionInput.graphQLContext)
            SEEN << Thread.currentThread()
            // a fresh input: the library's entry in the context is gone
            Mono.just(ExecutionInput.newExecutionInput().query(executionInput.query).build())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.probe')
    @Singleton
    static class ProbeCustomizer implements GraphQLExecutionInputCustomizer {
        static final AtomicInteger CALLED = new AtomicInteger()
        static final AtomicInteger SUBSCRIBED = new AtomicInteger()
        static final AtomicInteger CANCELLED = new AtomicInteger()
        static volatile boolean NEVER

        static void reset() {
            CALLED.set(0)
            SUBSCRIBED.set(0)
            CANCELLED.set(0)
            NEVER = false
        }

        @Override
        Publisher<ExecutionInput> customize(ExecutionInput executionInput, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            CALLED.incrementAndGet()
            Mono<ExecutionInput> input = NEVER ? Mono.<ExecutionInput> never() : Mono.deferContextual { ctx ->
                SEEN << ctx.getOrDefault('probe-key', 'missing')
                Mono.just(executionInput)
            }
            input.doOnSubscribe { SUBSCRIBED.incrementAndGet() }.doOnCancel { CANCELLED.incrementAndGet() }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.window')
    @Singleton
    static class WindowCustomizer implements GraphQLExecutionInputCustomizer {
        static final CountDownLatch TIMER_RAN = new CountDownLatch(1)
        static final CountDownLatch CANCELLED = new CountDownLatch(1)

        @Override
        Publisher<ExecutionInput> customize(ExecutionInput executionInput, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            Mono.<ExecutionInput> never()
                    .doOnSubscribe { assert TIMER_RAN.await(10, TimeUnit.SECONDS) }
                    .doOnCancel { CANCELLED.countDown() }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.window')
    @Singleton
    @Replaces(value = ExecutorService, named = TaskExecutors.SCHEDULED)
    @Named(TaskExecutors.SCHEDULED)
    static class RecordingScheduler extends ScheduledThreadPoolExecutor {
        RecordingScheduler() {
            super(1)
        }

        @Override
        ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            super.schedule({
                try {
                    command.run()
                } finally {
                    WindowCustomizer.TIMER_RAN.countDown()
                }
            } as Runnable, delay, unit)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.subclass')
    @Singleton
    static class SubclassedCustomizer extends DefaultGraphQLExecutionInputCustomizer {
        @Override
        Publisher<ExecutionInput> customize(ExecutionInput executionInput, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            CUSTOMIZER_THREADS << Thread.currentThread()
            super.customize(executionInput, httpRequest, httpResponse)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.recording')
    @Singleton
    static class RecordingCustomizer implements GraphQLExecutionInputCustomizer {
        @Override
        Publisher<ExecutionInput> customize(ExecutionInput executionInput, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            CUSTOMIZER_THREADS << Thread.currentThread()
            Mono.just(executionInput)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class Executors0 {
        @Bean
        @Singleton
        @Named('queued')
        ExecutorService queued() {
            Executors.newSingleThreadExecutor()
        }

        @Bean
        @Singleton
        @Named('throwing')
        ExecutorService throwing() {
            new ThrowingExecutor()
        }

        @Bean
        @Singleton
        @Named('rejecting')
        ExecutorService rejecting() {
            def executor = Executors.newSingleThreadExecutor()
            executor.shutdown()
            executor
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.replaces')
    @Singleton
    @Replaces(DefaultGraphQLInvocation)
    static class ReplacingInvocation implements GraphQLInvocation {
        @Override
        Publisher<ExecutionResult> invoke(GraphQLInvocationData invocationData, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            Mono.empty()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.primary')
    @Singleton
    @Primary
    static class PrimaryInvocation implements GraphQLInvocation {
        @Override
        Publisher<ExecutionResult> invoke(GraphQLInvocationData invocationData, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            Mono.empty()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.factory')
    @Factory
    static class InvocationFactory {
        @Bean
        @Primary
        @Singleton
        DefaultGraphQLInvocation invocation() {
            new DefaultGraphQLInvocation(null, null, null)
        }
    }

    static class ThrowingExecutor extends AbstractExecutorService {
        @Override
        void execute(Runnable command) {
            throw new IllegalStateException('execute failed')
        }

        @Override
        void shutdown() {
        }

        @Override
        List<Runnable> shutdownNow() {
            []
        }

        @Override
        boolean isShutdown() {
            false
        }

        @Override
        boolean isTerminated() {
            false
        }

        @Override
        boolean awaitTermination(long timeout, TimeUnit unit) {
            true
        }
    }

}
