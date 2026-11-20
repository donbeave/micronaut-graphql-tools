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
package io.micronaut.graphql.tools.benchmarks;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The cost of the executor hop of the execution model, on plain graphql-java (no library code runs): one operation
 * over a root object or over 1,000 objects, each with ten property fields and one method field.
 *
 * <ul>
 *   <li>{@code direct}: {@code executeAsync} on the calling thread, as when the executor is {@code none};</li>
 *   <li>{@code hopVirtual}, {@code hopPlatform}: the calling thread submits the whole execution to a virtual-thread
 *   executor or to a fixed pool of platform threads, and waits: one hop per request;</li>
 *   <li>{@code perFieldHop}: the execution starts on the calling thread and every method fetcher is wrapped in
 *   {@code supplyAsync} on the virtual-thread executor: a hop per method field (property fields never hop).</li>
 * </ul>
 *
 * Each trial first runs the operation once with a {@link ThreadChanges} counter attached and fails when the number of
 * thread changes is not the expected one. The measured operations carry no counter.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class InvocationBenchmark {

    @Param({"direct", "hopVirtual", "hopPlatform", "perFieldHop"})
    public String mode;

    @Param({"root", "items1000"})
    public String query;

    private Run run;

    @Setup
    public void prepare() {
        ThreadChanges counter = new ThreadChanges();
        try (Run verification = new Run(mode, query, counter)) {
            counter.begin();
            ExecutionResult result = verification.execute();
            if (!result.getErrors().isEmpty()) {
                throw new IllegalStateException(result.getErrors().toString());
            }
            boolean items = query.equals("items1000");
            int methodFields = items ? 1 + HopSchema.ITEMS : 2;
            int fetches = items ? 1 + HopSchema.ITEMS * (HopSchema.PROPERTY_FIELDS + 1) : HopSchema.PROPERTY_FIELDS + 2;
            switch (mode) {
                case "direct" -> counter.assertCounts(mode, 0, 0, fetches, 0, 0);
                case "hopVirtual", "hopPlatform" -> counter.assertCounts(mode, 1, 1, fetches, fetches, 0);
                case "perFieldHop" -> counter.assertCounts(mode, methodFields, methodFields, fetches, -1, 0);
                default -> throw new IllegalArgumentException(mode);
            }
        }
        run = new Run(mode, query, null);
    }

    @TearDown
    public void tearDown() {
        run.close();
    }

    @Benchmark
    public ExecutionResult execute() {
        return run.execute();
    }

    /** One mode over one query, with the counter of a verification run or none. */
    private static final class Run implements AutoCloseable {

        private final String mode;
        private final String query;
        private final GraphQL graphQL;
        private final ExecutorService executor;
        private final java.util.concurrent.Executor submit;

        Run(String mode, String queryName, ThreadChanges counter) {
            this.mode = mode;
            this.query = queryName.equals("items1000") ? HopSchema.ITEMS_QUERY : HopSchema.ROOT_QUERY;
            this.executor = switch (mode) {
                case "hopPlatform" -> Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), task -> {
                    Thread thread = new Thread(task, "hop-platform");
                    thread.setDaemon(true);
                    return thread;
                });
                case "direct" -> null;
                default -> Executors.newVirtualThreadPerTaskExecutor();
            };
            this.submit = executor == null || counter == null ? executor : counter.counting(executor);
            Map<String, HopSchema.Method> methods = Map.of("root", env -> HopSchema.NODE, "items", env -> HopSchema.NODES);
            this.graphQL = HopSchema.build(methods, mode.equals("perFieldHop")
                    ? method -> (DataFetcher<Object>) env -> CompletableFuture.supplyAsync(() -> method.apply(env), submit)
                    : method -> method::apply, counter);
        }

        ExecutionResult execute() {
            ExecutionInput input = ExecutionInput.newExecutionInput().query(query).build();
            if (mode.startsWith("hop")) {
                return CompletableFuture.supplyAsync(() -> graphQL.executeAsync(input), submit)
                        .thenCompose(future -> future).join();
            }
            return graphQL.executeAsync(input).join();
        }

        @Override
        public void close() {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }
}
