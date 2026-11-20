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

import graphql.GraphQLError
import graphql.ExecutionResult
import graphql.GraphQLContext
import graphql.execution.AbortExecutionException
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.customizer.GraphQLErrorsCustomizer
import io.micronaut.graphql.tools.internal.execution.AsyncBoundaryDataFetcher
import io.micronaut.graphql.tools.internal.execution.ExecutionState
import io.micronaut.graphql.tools.internal.execution.ExecutorHopDataFetcher
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import reactor.core.publisher.Mono

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The cancellation of an execution: the flag, and what it stops. */
class CancellationSpec extends AbstractTest {

    static final String SPEC_NAME = 'CancellationSpec'

    static final AtomicInteger HELLO_CALLS = new AtomicInteger()
    static final AtomicInteger SECOND_CALLS = new AtomicInteger()
    static volatile CompletableFuture<Object> FIRST = new CompletableFuture<>()

    @Language('GraphQL')
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
  first: First
}

type First {
  second: String
}
"""

    void setup() {
        HELLO_CALLS.set(0)
        SECOND_CALLS.set(0)
        FIRST = new CompletableFuture<>()
        FIRST_CALLED.set(false)
    }

    private Mono<ExecutionResult> invoke(String query) {
        Mono.from(applicationContext.getBean(GraphQLInvocation)
                .invoke(new GraphQLInvocationData(query, null, [:]), null, null))
    }

    private static DataFetchingEnvironment environment(ExecutionState state) {
        GraphQLContext graphQlContext = GraphQLContext.newContext().build()
        state.storeIn(graphQlContext)
        DataFetchingEnvironmentImpl.newDataFetchingEnvironment().graphQLContext(graphQlContext).build()
    }

    private static ExecutionState state(java.util.concurrent.Executor executor = null) {
        new ExecutionState(PropagatedContext.empty(), GraphQLTransport.HTTP, null, null, ConvertibleValues.empty(), executor, null)
    }

    void "cancel sets the flag, and nothing else"() {
        given:
        ExecutionState state = state()

        expect:
        !state.cancelled

        when:
        state.cancel()

        then:
        state.cancelled
    }

    void "cancelling the response of an HTTP operation that is still queued skips its hop task"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.executor': 'queued'])
        ExecutorService queued = applicationContext.getBean(ExecutorService, io.micronaut.inject.qualifiers.Qualifiers.byName('queued'))
        def release = new CountDownLatch(1)
        queued.execute { release.await() }

        when: 'the operation waits behind the blocker and the client disconnects'
        def future = invoke('{ hello }').toFuture()
        future.cancel(true)
        release.countDown()
        queued.shutdown()
        queued.awaitTermination(10, TimeUnit.SECONDS)

        then: 'no resolver ran'
        HELLO_CALLS.get() == 0
        future.isCancelled()
    }

    void "an operation that is cancelled before its hop task starts ends with EXECUTION_CANCELLED, through the errors customizers"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.executor': 'capturing'])
        CapturingExecutor executor = applicationContext.getBean(CapturingExecutor)

        when:
        def future = invoke('{ hello }').toFuture()
        executor.task.state.cancel()
        executor.task.run()
        ExecutionResult result = future.get(5, TimeUnit.SECONDS)

        then:
        HELLO_CALLS.get() == 0
        result.data == null
        result.errors.size() == 1
        result.errors[0].extensions.code == 'EXECUTION_CANCELLED'
        result.errors[0].extensions.customized == true
    }

    void "the async fields after a field that the disconnect overtook do not run"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when: 'the client disconnects while the first field is pending, and the field completes afterwards'
        def future = invoke('{ first { second } }').toFuture()
        awaitFirstCall()
        future.cancel(true)
        FIRST.complete(new First())
        Thread.sleep(200)

        then: 'the child field never ran'
        SECOND_CALLS.get() == 0
    }

    void "without a disconnect the same operation runs its later fields"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def future = invoke('{ first { second } }').toFuture()
        awaitFirstCall()
        FIRST.complete(new First())
        ExecutionResult result = future.get(5, TimeUnit.SECONDS)

        then:
        result.data == [first: [second: 'ran']]
        SECOND_CALLS.get() == 1
    }

    void "an async boundary of a cancelled execution completes with an abort"() {
        given:
        ExecutionState state = state()
        CompletableFuture<Object> source = new CompletableFuture<>()
        def fetcher = new AsyncBoundaryDataFetcher({ env -> source } as DataFetcher<Object>)

        when:
        CompletableFuture<Object> boundary = fetcher.get(environment(state)) as CompletableFuture<Object>
        state.cancel()
        source.complete('value')
        boundary.get(5, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof AbortExecutionException
    }

    void "a field hop task of a cancelled execution is skipped"() {
        given:
        ExecutionState state = state()
        ExecutorService pool = Executors.newSingleThreadExecutor()
        def release = new CountDownLatch(1)
        pool.execute { release.await() }
        def calls = new AtomicInteger()
        def fetcher = new ExecutorHopDataFetcher(pool, { env -> calls.incrementAndGet() } as DataFetcher<Object>)

        when:
        CompletableFuture<Object> field = fetcher.get(environment(state)) as CompletableFuture<Object>
        state.cancel()
        release.countDown()
        field.get(5, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof AbortExecutionException
        calls.get() == 0

        cleanup:
        pool.shutdownNow()
    }

    private static void awaitFirstCall() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!FIRST_CALLED.get() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assert FIRST_CALLED.get()
    }

    static final java.util.concurrent.atomic.AtomicBoolean FIRST_CALLED = new java.util.concurrent.atomic.AtomicBoolean()

    @GraphQLType
    static class First {
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello() {
            HELLO_CALLS.incrementAndGet()
            'world'
        }

        CompletionStage<First> first() {
            FIRST_CALLED.set(true)
            FIRST
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(First)
    static class FirstFields {
        String second(First first) {
            SECOND_CALLS.incrementAndGet()
            'ran'
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class Customizer implements GraphQLErrorsCustomizer {
        @Override
        List<GraphQLError> customize(List<GraphQLError> errors) {
            errors.collect {
                graphql.GraphqlErrorBuilder.newError().message(it.message)
                        .extensions(new LinkedHashMap(it.extensions) << [customized: true]).build()
            }
        }
    }

    static class CapturingExecutor extends java.util.concurrent.AbstractExecutorService {
        volatile Runnable task

        @Override
        void execute(Runnable command) {
            task = command
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
        @Named('capturing')
        CapturingExecutor capturing() {
            new CapturingExecutor()
        }
    }
}
