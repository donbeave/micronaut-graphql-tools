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
package example.cancel;

import graphql.ExecutionResult;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocationData;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): cancelling an execution cancels its pending async stages.
class CancelPendingStagesTest {

    private ApplicationContext context;

    @BeforeEach
    void start() {
        CancelFixtures.held = new CompletableFuture<>();
        CancelFixtures.heldStarted = new CountDownLatch(1);
        context = ApplicationContext.run(Map.of(), CancelFixtures.CANCEL_PENDING);
    }

    @AfterEach
    void stop() {
        context.close();
    }

    private CompletableFuture<ExecutionResult> execute(String query) {
        return Mono.from(context.getBean(GraphQLInvocation.class).invoke(new GraphQLInvocationData(query, null, Map.of()), null, null)).toFuture();
    }

    @Test
    void cancellingAnExecutionCancelsItsPendingStageAndLeavesAnotherExecutionAlone() throws Exception {
        CompletableFuture<ExecutionResult> cancelled = execute("{ held }");
        assertTrue(CancelFixtures.heldStarted.await(10, TimeUnit.SECONDS));
        CompletableFuture<String> heldStage = CancelFixtures.held;
        CompletableFuture<ExecutionResult> other = execute("{ quick }");

        assertTrue(cancelled.cancel(true));

        // the stage may not be registered yet when the cancel arrives; it is then aborted as soon as it registers
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!heldStage.isCancelled() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(heldStage.isCancelled());
        ExecutionResult result = other.get(10, TimeUnit.SECONDS);
        assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
        assertEquals(Map.of("quick", "quick"), result.getData());
    }

    @Test
    void anExecutionThatIsNotCancelledKeepsItsResult() throws Exception {
        CompletableFuture<ExecutionResult> running = execute("{ held }");
        assertTrue(CancelFixtures.heldStarted.await(10, TimeUnit.SECONDS));
        CancelFixtures.held.complete("done");

        ExecutionResult result = running.get(10, TimeUnit.SECONDS);
        assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
        assertEquals(Map.of("held", "done"), result.getData());
    }
}
