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
package io.micronaut.graphql.tools.tck;

import graphql.ExecutionResult;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocationData;
import io.micronaut.context.ApplicationContext;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The checks of the execution-input customizer path, run by each example suite against its own fixtures of the scenario
 * {@code customizer}: the customizer runs only when the returned publisher is subscribed, it sees the Reactor context of
 * the subscriber, and cancelling the subscription cancels the publisher of the customizer and stops the execution. The
 * checks call the {@code GraphQLInvocation} bean directly, because an HTTP request always subscribes and carries no Reactor
 * context. The fixture customizer acts only on an operation whose variables hold {@code track: true}, so the operations
 * that read the counters do not count themselves. Each check starts the application of its own.
 */
public final class CustomizerTck {

    private static final String TRACKED = "{ tracked }";
    private static final String COUNTS = "{ customizerCalls customizerSubscribes customizerCancels customizerSeen trackedRuns }";
    private static final String CONTEXT_KEY = "tck-key";
    private static final long SECONDS = 10;

    private static final List<String> CHECKS = List.of(
            "customizer-runs-only-on-subscription",
            "customizer-sees-reactor-context",
            "cancel-stops-execution");

    private CustomizerTck() {
    }

    /**
     * @return the names of the checks
     */
    public static List<String> checks() {
        return CHECKS;
    }

    /**
     * Runs one check against a fresh application.
     *
     * @param check one of {@link #checks()}
     */
    public static void run(String check) {
        if (!CHECKS.contains(check)) {
            throw new IllegalArgumentException("No such check: " + check);
        }
        try (ApplicationContext context = ApplicationContext.builder().properties(Tck.applicationProperties("customizer")).start()) {
            GraphQLInvocation invocation = context.getBean(GraphQLInvocation.class);
            switch (check) {
                case "customizer-runs-only-on-subscription" -> runsOnlyOnSubscription(invocation);
                case "customizer-sees-reactor-context" -> seesReactorContext(invocation);
                default -> cancelStopsExecution(invocation);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(check + ": interrupted", e);
        }
    }

    private static void runsOnlyOnSubscription(GraphQLInvocation invocation) throws InterruptedException {
        var publisher = invoke(invocation, TRACKED, Map.of("track", true));
        Thread.sleep(300);
        expect(0, count(invocation, "customizerCalls"), "customizer calls before the publisher was subscribed");
        expect(0, count(invocation, "trackedRuns"), "executions before the publisher was subscribed");
        ExecutionResult result = Mono.from(publisher).block(Duration.ofSeconds(SECONDS));
        expect(true, result != null && result.getErrors().isEmpty(), "the operation succeeds: " + result);
        expect(1, count(invocation, "customizerCalls"), "customizer calls after the subscription");
        expect(1, count(invocation, "trackedRuns"), "executions after the subscription");
    }

    private static void seesReactorContext(GraphQLInvocation invocation) {
        ExecutionResult result = Mono.from(invoke(invocation, TRACKED, Map.of("track", true)))
                .contextWrite(Context.of(CONTEXT_KEY, "from-subscriber")).block(Duration.ofSeconds(SECONDS));
        expect(true, result != null && result.getErrors().isEmpty(), "the operation succeeds: " + result);
        expect("from-subscriber", data(invocation).get("customizerSeen"), "the Reactor context entry the customizer saw");
    }

    private static void cancelStopsExecution(GraphQLInvocation invocation) throws InterruptedException {
        Disposable subscription = Mono.from(invoke(invocation, TRACKED, Map.of("track", true, "hold", true))).subscribe();
        awaitCount(invocation, "customizerSubscribes", 1);
        subscription.dispose();
        awaitCount(invocation, "customizerCancels", 1);
        Thread.sleep(300);
        expect(0, count(invocation, "trackedRuns"), "executions of an operation whose subscription was cancelled");
    }

    private static Publisher<ExecutionResult> invoke(GraphQLInvocation invocation, String query, Map<String, Object> variables) {
        return invocation.invoke(new GraphQLInvocationData(query, null, variables), null, null);
    }

    private static Map<String, Object> data(GraphQLInvocation invocation) {
        ExecutionResult result = Mono.from(invoke(invocation, COUNTS, Map.of())).block(Duration.ofSeconds(SECONDS));
        if (result == null || !result.getErrors().isEmpty()) {
            throw new AssertionError("the counters could not be read: " + result);
        }
        return result.getData();
    }

    private static int count(GraphQLInvocation invocation, String field) {
        return ((Number) data(invocation).get(field)).intValue();
    }

    private static void awaitCount(GraphQLInvocation invocation, String field, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SECONDS);
        int actual = count(invocation, field);
        while (actual != expected && System.nanoTime() < deadline) {
            Thread.sleep(20);
            actual = count(invocation, field);
        }
        expect(expected, actual, field);
    }

    private static void expect(Object expected, Object actual, Object context) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">: " + context);
        }
    }
}
