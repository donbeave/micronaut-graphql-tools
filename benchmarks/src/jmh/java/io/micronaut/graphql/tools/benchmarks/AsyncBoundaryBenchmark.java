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
import reactor.core.scheduler.NonBlocking;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A prototype of the async boundary, in this benchmark source only: a field that returns a {@link CompletionStage}
 * completes on some thread, and graphql-java runs the children of the field on that thread. When it is an event-loop
 * thread (one that implements Reactor's {@link NonBlocking} marker), the children would run on the event loop, so the
 * boundary completes the field's future on the request executor instead. {@code completion} says where the stage is
 * completed, {@code boundary} whether the prototype wraps the field; the query reads ten property fields of the
 * object the field returns. Like {@link InvocationBenchmark}, each trial asserts the counted hops first.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class AsyncBoundaryBenchmark {

    /** {@code sameThread}: already completed when returned; {@code poolThread}: a platform pool thread; {@code nonBlockingThread}: a thread that is {@link NonBlocking}. */
    @Param({"sameThread", "poolThread", "nonBlockingThread"})
    public String completion;

    @Param({"off", "on"})
    public String boundary;

    private Run run;

    @Setup
    public void prepare() {
        ThreadChanges counter = new ThreadChanges();
        try (Run verification = new Run(completion, boundary, counter)) {
            counter.begin();
            ExecutionResult result = verification.execute();
            if (!result.getErrors().isEmpty()) {
                throw new IllegalStateException(result.getErrors().toString());
            }
            int fetches = HopSchema.PROPERTY_FIELDS + 2;
            String what = completion + "/" + boundary;
            int children = HopSchema.PROPERTY_FIELDS + 1;
            switch (completion) {
                // already completed: the children run where the field was fetched
                case "sameThread" -> counter.assertCounts(what, 0, 0, fetches, 0, 0);
                case "poolThread" -> counter.assertCounts(what, 0, 0, fetches, children, 0);
                case "nonBlockingThread" -> {
                    if (boundary.equals("on")) {
                        counter.assertCounts(what, 1, 1, fetches, children, 0);
                    } else {
                        counter.assertCounts(what, 0, 0, fetches, children, children);
                    }
                }
                default -> throw new IllegalArgumentException(completion);
            }
        }
        run = new Run(completion, boundary, null);
    }

    @TearDown
    public void tearDown() {
        run.close();
    }

    @Benchmark
    public ExecutionResult execute() {
        return run.execute();
    }

    private static final class EventLoopThread extends Thread implements NonBlocking {

        EventLoopThread(Runnable task) {
            super(task, "event-loop");
            setDaemon(true);
        }
    }

    private static final class Run implements AutoCloseable {

        private final GraphQL graphQL;
        private volatile CompletableFuture<Void> attached = CompletableFuture.completedFuture(null);
        private final ExecutorService requestExecutor = Executors.newVirtualThreadPerTaskExecutor();
        private final ExecutorService completionExecutor;

        Run(String completion, String boundary, ThreadChanges counter) {
            completionExecutor = switch (completion) {
                case "poolThread" -> Executors.newSingleThreadExecutor(task -> {
                    Thread thread = new Thread(task, "pool");
                    thread.setDaemon(true);
                    return thread;
                });
                case "nonBlockingThread" -> Executors.newSingleThreadExecutor(EventLoopThread::new);
                default -> null;
            };
            Executor request = counter == null ? requestExecutor : counter.counting(requestExecutor);
            boolean sameThread = completionExecutor == null;
            boolean boundaryOn = boundary.equals("on");
            Map<String, HopSchema.Method> methods = Map.of("asyncRoot", env -> {
                CompletableFuture<Object> source = new CompletableFuture<>();
                CompletableFuture<Object> field = boundaryOn ? boundary(source, request) : source;
                if (sameThread) {
                    source.complete(HopSchema.NODE);
                } else {
                    CompletableFuture<Void> gate = attached;
                    completionExecutor.execute(() -> {
                        // complete only after executeAsync returned, when graphql-java has attached everything that
                        // follows the field: an earlier completion would run the children on the calling thread
                        gate.join();
                        source.complete(HopSchema.NODE);
                    });
                }
                return field;
            });
            graphQL = HopSchema.build(methods, method -> (DataFetcher<Object>) method::apply, counter);
        }

        /** The prototype: a future that completes on the request executor when the stage completes on an event loop. */
        private static CompletableFuture<Object> boundary(CompletionStage<Object> stage, Executor requestExecutor) {
            CompletableFuture<Object> field = new CompletableFuture<>();
            stage.whenComplete((value, error) -> {
                Runnable complete = () -> {
                    if (error != null) {
                        field.completeExceptionally(error);
                    } else {
                        field.complete(value);
                    }
                };
                if (Thread.currentThread() instanceof NonBlocking) {
                    requestExecutor.execute(complete);
                } else {
                    complete.run();
                }
            });
            return field;
        }

        ExecutionResult execute() {
            CompletableFuture<Void> gate = new CompletableFuture<>();
            attached = gate;
            CompletableFuture<ExecutionResult> result = graphQL.executeAsync(
                    ExecutionInput.newExecutionInput().query(HopSchema.ASYNC_QUERY).build());
            gate.complete(null);
            return result.join();
        }

        @Override
        public void close() {
            requestExecutor.shutdown();
            if (completionExecutor != null) {
                completionExecutor.shutdown();
            }
        }
    }
}
