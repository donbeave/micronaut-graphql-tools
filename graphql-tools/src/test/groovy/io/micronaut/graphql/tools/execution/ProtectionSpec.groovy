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

import graphql.ExecutionResult
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.language.Document
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import reactor.core.publisher.Mono

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The protection settings of the library's GraphQL bean: the document cache, the limits, introspection and the timeout. */
class ProtectionSpec extends AbstractSpec {

    static final String SPEC_NAME = 'ProtectionSpec'

    static final AtomicInteger PARSES = new AtomicInteger()
    static final AtomicInteger SECOND_CALLS = new AtomicInteger()
    static volatile CompletableFuture<Object> FIRST = new CompletableFuture<>()
    static final HeldExecutor HELD = new HeldExecutor()

    @Language('GraphQL')
    static final String SCHEMA = """
type Query {
  hello: String
  node: Node
  first: First
}

type Node {
  name: String
  child: Node
}

type First {
  second: String
}
"""

    void setup() {
        PARSES.set(0)
        SECOND_CALLS.set(0)
        FIRST = new CompletableFuture<>()
        HELD.tasks.clear()
    }

    private ExecutionResult run(String query) {
        executeQuery(query)
    }

    void "the same query text is parsed once"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def results = (1..5).collect { run('{ hello }') }

        then:
        results.every { it.data == [hello: 'world'] }
        PARSES.get() == 1
    }

    void "a different query text is parsed again, and variables do not change the key"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        run('{ hello }')
        run('{ node { name } }')
        run('{ hello }')

        then:
        PARSES.get() == 2
    }

    void "a query that is invalid stays invalid and is not parsed again"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def first = run('{ nothing }')
        def second = run('{ nothing }')

        then:
        first.errors.size() == 1
        second.errors*.message == first.errors*.message
        PARSES.get() == 1
    }

    void "a cache size of 0 parses every time"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.protection.document-cache-size': 0])

        when:
        3.times { run('{ hello }') }

        then:
        PARSES.get() == 3
    }

    void "the cache is bounded: the least recently used document goes when it is full"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.protection.document-cache-size': 2])

        when:
        run('{ hello }')
        run('{ node { name } }')
        run('{ hello }')
        run('{ node { child { name } } }')
        int before = PARSES.get()
        run('{ hello }')
        int afterRecent = PARSES.get()
        run('{ node { name } }')

        then:
        before == 3
        afterRecent == 3
        PARSES.get() == 4
    }

    void "a query over the depth limit is rejected with a GraphQL error and a query within it runs"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.protection.max-depth': 3])

        when:
        def within = run('{ node { child { name } } }')
        def over = run('{ node { child { child { name } } } }')

        then:
        within.errors.isEmpty()
        within.data.node.child.name == 'leaf'
        over.errors.size() == 1
        over.errors[0].message.toLowerCase().contains('depth')
    }

    void "a query over the complexity limit is rejected with a GraphQL error and a query within it runs"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.protection.max-complexity': 3])

        when:
        def within = run('{ node { child { name } } }')
        def over = run('{ node { name child { name } } }')

        then:
        within.errors.isEmpty()
        over.errors.size() == 1
        over.errors[0].message.toLowerCase().contains('complexity')
    }

    void "introspection is available by default"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        expect:
        run('{ __schema { queryType { name } } }').data.__schema.queryType.name == 'Query'
        run('{ __type(name: "Node") { name } }').data.__type.name == 'Node'
    }

    void "introspection that is turned off is rejected with an error, and __typename and other fields keep working"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.protection.introspection': false])

        when:
        def schema = run('{ __schema { queryType { name } } }')
        def type = run('{ __type(name: "Node") { name } }')
        def typename = run('{ __typename hello }')

        then:
        schema.errors.size() == 1
        schema.data == null
        type.errors.size() == 1
        typename.errors.isEmpty()
        typename.data == [__typename: 'Query', hello: 'world']
    }

    void "a timeout answers a slow operation with an error and cancels it, so the later async fields do not run"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.timeout': '300ms'])

        when:
        ExecutionResult result = invoke('{ first { second } }').block(java.time.Duration.ofSeconds(5))
        FIRST.complete(new First())
        Thread.sleep(300)

        then:
        result.data == null
        result.errors.size() == 1
        result.errors[0].extensions.code == 'EXECUTION_TIMEOUT'
        SECOND_CALLS.get() == 0
    }

    void "a stage that completes after the client has its timeout answer does not start a later field, even when the cancellation task has not run"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.timeout': '200ms',
                                         'micronaut.graphql.tools.execution.executor': 'held',
                                         'spec.held': 'true'])

        when:
        def future = invoke('{ first { second } }').toFuture()
        HELD.take().run()
        def completion = HELD.take()
        def cancellation = HELD.take()
        completion.run()
        ExecutionResult result = future.get(5, TimeUnit.SECONDS)
        FIRST.complete(new First())
        int secondCallsBeforeCancellation = SECOND_CALLS.get()
        cancellation.run()

        then:
        result.errors[0].extensions.code == 'EXECUTION_TIMEOUT'
        secondCallsBeforeCancellation == 0
        SECOND_CALLS.get() == 0
    }

    void "an operation that finishes within the timeout is not affected"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.timeout': '5s'])

        when:
        ExecutionResult result = invoke('{ hello }').block(java.time.Duration.ofSeconds(5))

        then:
        result.data == [hello: 'world']
        result.errors.isEmpty()
    }

    void "without a timeout a slow operation waits"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def future = invoke('{ first { second } }').toFuture()
        Thread.sleep(300)

        then:
        !future.isDone()

        when:
        FIRST.complete(new First())
        ExecutionResult result = future.get(5, TimeUnit.SECONDS)

        then:
        result.data == [first: [second: 'ran']]
    }

    void "a timeout that is not positive fails the startup"() {
        when:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.execution.timeout': '0s'])
        getGraphQLBean()
        applicationContext.getBean(GraphQLInvocation)

        then:
        def e = thrown(Exception)
        generateMessage(e).contains('timeout')
    }

    private static String generateMessage(Throwable e) {
        StringBuilder text = new StringBuilder()
        for (Throwable t = e; t != null; t = t.cause) {
            text.append(t.message).append(' ')
        }
        text.toString()
    }

    private Mono<ExecutionResult> invoke(String query) {
        Mono.from(applicationContext.getBean(GraphQLInvocation)
                .invoke(new GraphQLInvocationData(query, null, [:]), null, null))
    }

    /** An executor that queues every task until the test takes it, so that the test decides the order of the timeout work. */
    static class HeldExecutor extends AbstractExecutorService {
        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>()

        Runnable take() {
            Runnable task = tasks.poll(5, TimeUnit.SECONDS)
            assert task != null: 'the executor of the operations got no task'
            task
        }

        @Override
        void execute(Runnable command) {
            tasks.add(command)
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
    @Requires(property = 'spec.held')
    @Factory
    static class HeldExecutors {
        @Bean
        @Singleton
        @Named('held')
        ExecutorService held() {
            HELD
        }
    }

    @GraphQLType
    static class Node {
        String name
        Node child
    }

    @GraphQLType
    static class First {
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello() {
            'world'
        }

        Node node() {
            new Node(name: 'root', child: new Node(name: 'leaf', child: new Node(name: 'deep')))
        }

        CompletionStage<First> first() {
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
    static class ParseCounter extends SimplePerformantInstrumentation {
        @Override
        InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
            PARSES.incrementAndGet()
            super.beginParse(parameters, state)
        }
    }
}
