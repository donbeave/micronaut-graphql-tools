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

import jakarta.inject.Singleton
import graphql.ExecutionResult
import graphql.GraphQLContext
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.schema.FieldCoordinates
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.execution.GraphQLTransport
import io.micronaut.graphql.tools.internal.execution.AsyncBoundaryDataFetcher
import io.micronaut.graphql.tools.internal.execution.ExecutionState
import io.micronaut.graphql.tools.internal.resolver.MicronautExecutableMethodDataFetcher
import io.micronaut.graphql.tools.internal.resolver.MicronautPropertyDataFetcher
import org.intellij.lang.annotations.Language
import reactor.core.publisher.Mono
import reactor.core.scheduler.NonBlocking

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

/**
 * The async boundary: a child of an async field sees the propagated context of the request and does not run on the thread
 * that completed its parent when that is an event-loop thread.
 */
class AsyncBoundarySpec extends AbstractTest {

    static final String SPEC_NAME = 'AsyncBoundarySpec'

    static final Map<String, Thread> CHILD_THREADS = Collections.synchronizedMap([:])
    static final Map<String, Object> CHILD_MARKERS = Collections.synchronizedMap([:])

    /** A thread that the test declares to be an event-loop thread, as Micronaut's event-loop threads are. */
    static class FakeEventLoopThread extends Thread implements NonBlocking {
        FakeEventLoopThread(Runnable task) {
            super(task, 'fake-event-loop')
        }
    }

    static ExecutorService fakeEventLoop
    static ExecutorService otherPool

    @Language('GraphQL')
    static final String SCHEMA = """
type Query {
  viaPool: Parent
  viaEventLoop: Parent
  viaMono: Parent
  viaOptional: Parent
  failing: Parent
  failingEventLoop: Parent
  plain: String
}

type Parent {
  label: String
  marker: String
}
"""

    void setupSpec() {
        fakeEventLoop = Executors.newSingleThreadExecutor { new FakeEventLoopThread(it) }
        otherPool = Executors.newSingleThreadExecutor { new Thread(it, 'other-pool') }
    }

    void cleanupSpec() {
        fakeEventLoop.shutdownNow()
        otherPool.shutdownNow()
    }

    void setup() {
        CHILD_THREADS.clear()
        CHILD_MARKERS.clear()
    }

    private ExecutionResult run(String query, MarkerElement element = new MarkerElement('request-1')) {
        def scope = PropagatedContext.getOrEmpty().plus(element).propagate()
        try {
            return Mono.from(applicationContext.getBean(GraphQLInvocation)
                    .invoke(new GraphQLInvocationData(query, null, [:]), null, null)).block()
        } finally {
            scope.close()
        }
    }

    void "the propagated context is visible in a child of a field whose future completes on another pool"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = run('{ viaPool { marker } }')

        then:
        result.errors.isEmpty()
        result.data == [viaPool: [marker: 'request-1']]
        CHILD_MARKERS['marker'] == new MarkerElement('request-1')
        // a pool that is not an event loop keeps its children: the library does not hop again
        CHILD_THREADS['marker'].name == 'other-pool'
    }

    void "a child of a field whose future completes on an event-loop thread runs off that thread, with the context"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = run('{ viaEventLoop { marker } }')

        then:
        result.errors.isEmpty()
        result.data == [viaEventLoop: [marker: 'request-1']]
        CHILD_MARKERS['marker'] == new MarkerElement('request-1')
        !(CHILD_THREADS['marker'] instanceof NonBlocking)
    }

    void "with no request executor the context is still restored, on the thread that completed"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.executor': 'none'])

        when:
        def result = run('{ viaEventLoop { marker } }')

        then:
        result.errors.isEmpty()
        CHILD_MARKERS['marker'] == new MarkerElement('request-1')
        CHILD_THREADS['marker'] instanceof FakeEventLoopThread
    }

    void "a Mono that completes on a Reactor scheduler thread continues in the context, off that thread"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = run('{ viaMono { marker } }')

        then:
        result.errors.isEmpty()
        result.data == [viaMono: [marker: 'request-1']]
        CHILD_MARKERS['marker'] == new MarkerElement('request-1')
        !(CHILD_THREADS['marker'] instanceof NonBlocking)
    }

    void "an Optional inside a stage is unwrapped after the boundary"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = run('{ viaOptional { marker } }')

        then:
        result.errors.isEmpty()
        result.data == [viaOptional: [marker: 'request-1']]
    }

    void "a failure reaches the error handling as its real type, not as a CompletionException"() {
        given: 'the details setting shows the class that the error handling saw'
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.errors.details': true])

        when:
        def result = run(query)

        then:
        result.errors.size() == 1
        result.errors[0].message == 'boom'
        result.errors[0].extensions.exceptionClass == 'java.lang.IllegalStateException'

        where:
        query << ['{ failing { label } }', '{ failingEventLoop { label } }']
    }

    void "a field with a plain return and a property keep their fetchers, an async field gets the boundary"() {
        given:
        startContext(SCHEMA, SPEC_NAME)
        def schema = getGraphQLBean().graphQLSchema

        expect:
        fetcher(schema, 'Query', 'plain').class == MicronautExecutableMethodDataFetcher
        fetcher(schema, 'Parent', 'label').class == MicronautPropertyDataFetcher
        fetcher(schema, 'Parent', 'marker').class == MicronautExecutableMethodDataFetcher
        fetcher(schema, 'Query', 'viaPool').class == AsyncBoundaryDataFetcher
        fetcher(schema, 'Query', 'viaMono').class == AsyncBoundaryDataFetcher
    }

    void "a future that is already done on a thread that holds the context is returned as it is"() {
        given:
        def captured = PropagatedContext.getOrEmpty().plus(new MarkerElement('x'))
        def state = new ExecutionState(captured, GraphQLTransport.HTTP, null, null, ConvertibleValues.empty(), null, null)
        def context = GraphQLContext.newContext().build()
        state.storeIn(context)
        def done = CompletableFuture.completedFuture('value')
        def fetcher = new AsyncBoundaryDataFetcher({ done } as DataFetcher)
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().graphQLContext(context).build()

        when:
        def inContext = captured.propagate()
        Object result
        try {
            result = fetcher.get(environment)
        } finally {
            inContext.close()
        }

        then:
        result.is(done)
    }

    void "a failed future, a future of another thread and a result that is no stage are not returned as they are"() {
        given:
        def captured = PropagatedContext.getOrEmpty().plus(new MarkerElement('x'))
        def state = new ExecutionState(captured, GraphQLTransport.HTTP, null, null, ConvertibleValues.empty(), null, null)
        def context = GraphQLContext.newContext().build()
        state.storeIn(context)
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().graphQLContext(context).build()
        def failed = new CompletableFuture()
        failed.completeExceptionally(new CompletionException(new ExecutionException(new IllegalStateException('inner'))))
        def seenBy = [] as List

        when:
        def inContext = captured.propagate()
        def boundary
        try {
            boundary = new AsyncBoundaryDataFetcher({ failed } as DataFetcher).get(environment)
        } finally {
            inContext.close()
        }

        then: 'the failure is unwrapped down to its cause'
        !boundary.is(failed)
        boundary.isCompletedExceptionally()
        boundary.handle { v, e -> e }.get().class == IllegalStateException

        and: 'a null and a non-stage result pass through, and so does a call without the state of the library'
        new AsyncBoundaryDataFetcher({ null } as DataFetcher).get(environment) == null
        new AsyncBoundaryDataFetcher({ 'text' } as DataFetcher).get(environment) == 'text'
        def bare = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().graphQLContext(GraphQLContext.newContext().build()).build()
        def pending = new CompletableFuture()
        new AsyncBoundaryDataFetcher({ pending } as DataFetcher).get(bare).is(pending)
    }

    void "a minimal stage, which refuses to be inspected, is completed through its callback"() {
        given:
        def captured = PropagatedContext.getOrEmpty().plus(new MarkerElement('x'))
        def state = new ExecutionState(captured, GraphQLTransport.HTTP, null, null, ConvertibleValues.empty(), null, null)
        def context = GraphQLContext.newContext().build()
        state.storeIn(context)
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().graphQLContext(context).build()
        def completed = CompletableFuture.completedStage('value')
        def failed = CompletableFuture.failedStage(new IllegalStateException('inner'))

        when:
        def inContext = captured.propagate()
        def ok
        def bad
        try {
            ok = new AsyncBoundaryDataFetcher({ completed } as DataFetcher).get(environment)
            bad = new AsyncBoundaryDataFetcher({ failed } as DataFetcher).get(environment)
        } finally {
            inContext.close()
        }

        then:
        ok.get() == 'value'
        bad.handle { v, e -> e }.get().class == IllegalStateException
        when:
        ok.cancel(true)
        then:
        noExceptionThrown()
    }

    void "cancelling the future of the boundary cancels the source"() {
        given:
        def state = new ExecutionState(PropagatedContext.getOrEmpty(), GraphQLTransport.HTTP, null, null, ConvertibleValues.empty(), null, null)
        def context = GraphQLContext.newContext().build()
        state.storeIn(context)
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().graphQLContext(context).build()
        def source = new CompletableFuture()

        when:
        def boundary = new AsyncBoundaryDataFetcher({ source } as DataFetcher).get(environment)
        boundary.cancel(true)

        then:
        !boundary.is(source)
        source.isCancelled()
    }

    private static DataFetcher fetcher(schema, String type, String field) {
        def definition = schema.getObjectType(type).getFieldDefinition(field)
        schema.codeRegistry.getDataFetcher(FieldCoordinates.coordinates(type, field), definition)
    }

    record MarkerElement(String id) implements PropagatedContextElement {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {

        CompletableFuture<Parent> viaPool() {
            later(otherPool, new Parent())
        }

        CompletableFuture<Parent> viaEventLoop() {
            later(fakeEventLoop, new Parent())
        }

        Mono<Parent> viaMono() {
            Mono.just(new Parent()).delayElement(Duration.ofMillis(30))
        }

        CompletableFuture<Optional<Parent>> viaOptional() {
            later(otherPool, Optional.of(new Parent()))
        }

        CompletableFuture<Parent> failing() {
            CompletableFuture.supplyAsync({ throw new IllegalStateException('boom') }, otherPool)
        }

        CompletableFuture<Parent> failingEventLoop() {
            CompletableFuture.supplyAsync({
                Thread.sleep(30)
                throw new IllegalStateException('boom')
            }, fakeEventLoop)
        }

        String plain() {
            'plain'
        }

        private static <T> CompletableFuture<T> later(ExecutorService executor, T value) {
            def future = new AttachAwareFuture<T>()
            executor.execute {
                future.awaitCallerParked()
                future.complete(value)
            }
            future
        }
    }

    /**
     * A future that completes only after the thread that registered the library's callback has returned from the fetch and
     * parked, so that the callback and the continuations of graphql-java are all registered and none runs on that thread.
     */
    static class AttachAwareFuture<T> extends CompletableFuture<T> {
        private final CountDownLatch attached = new CountDownLatch(1)
        private volatile Thread caller

        @Override
        CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
            caller = Thread.currentThread()
            def registered = super.whenComplete(action)
            attached.countDown()
            registered
        }

        void awaitCallerParked() {
            attached.await(5, TimeUnit.SECONDS)
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                def state = caller?.state
                if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                    return
                }
                Thread.sleep(1)
            }
        }
    }

    @GraphQLType
    static class Parent {
        String label = 'parent'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLTypeResolver(Parent)
    static class ParentResolver {
        String marker(Parent parent) {
            CHILD_THREADS['marker'] = Thread.currentThread()
            def element = PropagatedContext.getOrEmpty().find(MarkerElement).orElse(null)
            CHILD_MARKERS['marker'] = element
            element?.id()
        }
    }
}
