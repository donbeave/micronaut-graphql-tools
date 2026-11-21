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
package example.cancel

import graphql.ExecutionResult
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.ApplicationContext
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The Groovy suite runs on the JVM: cancelling an execution cancels its pending async stages.
class CancelPendingStagesSpec extends Specification {

    ApplicationContext context

    void setup() {
        CancelFixtures.held = new CompletableFuture<>()
        CancelFixtures.heldStarted = new CountDownLatch(1)
        context = ApplicationContext.run([:], CancelFixtures.CANCEL_PENDING)
    }

    void cleanup() {
        context.close()
    }

    private CompletableFuture<ExecutionResult> execute(String query) {
        Mono.from(context.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, null, [:]), null, null)).toFuture()
    }

    void 'cancelling an execution cancels its pending stage and leaves another execution alone'() {
        given:
        def cancelled = execute('{ held }')
        assert CancelFixtures.heldStarted.await(10, TimeUnit.SECONDS)
        def heldStage = CancelFixtures.held
        def other = execute('{ quick }')

        when:
        boolean accepted = cancelled.cancel(true)
        ExecutionResult result = other.get(10, TimeUnit.SECONDS)

        then:
        accepted
        heldStage.cancelled
        result.errors.empty
        result.getData() == [quick: 'quick']
    }

    void 'an execution that is not cancelled keeps its result'() {
        given:
        def running = execute('{ held }')
        assert CancelFixtures.heldStarted.await(10, TimeUnit.SECONDS)

        when:
        CancelFixtures.held.complete('done')
        ExecutionResult result = running.get(10, TimeUnit.SECONDS)

        then:
        result.errors.empty
        result.getData() == [held: 'done']
    }
}
