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
package io.micronaut.graphql.tools.internal.execution;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs the method of a field on the executor that its {@code @ExecuteOn} names. The planner places it only where that
 * executor differs from the executor of the operation, so a field that already runs there pays nothing. It returns a
 * future that completes on the field's executor: the children of the field that are synchronous continue on that
 * executor, which is why it must be sized for the field and its children. When the method returns a stage, the future
 * follows it. The captured propagated context is installed in the task when the thread does not hold it already.
 */
@Internal
public final class ExecutorHopDataFetcher implements DataFetcher<Object> {

    private final Executor executor;
    private final DataFetcher<Object> delegate;

    /**
     * @param executor the executor of the field
     * @param delegate the fetcher that invokes the method, or an adapter around it
     */
    public ExecutorHopDataFetcher(Executor executor, DataFetcher<Object> delegate) {
        this.executor = executor;
        this.delegate = delegate;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        ExecutionState state = ExecutionState.of(environment.getGraphQlContext());
        PropagatedContext captured = state != null ? state.propagatedContext() : PropagatedContext.getOrEmpty();
        CompletableFuture<Object> result = new CompletableFuture<>();
        try {
            executor.execute(() -> run(environment, captured, result));
        } catch (RejectedExecutionException e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    private void run(DataFetchingEnvironment environment, PropagatedContext captured, CompletableFuture<Object> result) {
        if (result.isDone()) {
            return;
        }
        if (PropagatedContext.getOrEmpty() == captured) {
            invoke(environment, result);
            return;
        }
        try (PropagatedContext.Scope ignored = captured.propagate()) {
            invoke(environment, result);
        }
    }

    private void invoke(DataFetchingEnvironment environment, CompletableFuture<Object> result) {
        try {
            Object value = delegate.get(environment);
            if (value instanceof CompletionStage<?> stage) {
                Thread hopThread = Thread.currentThread();
                stage.whenComplete((v, failure) -> {
                    if (Thread.currentThread() == hopThread) {
                        finish(result, v, failure);
                        return;
                    }
                    // the stage completed elsewhere: the field completes on its executor, so its children continue there
                    try {
                        executor.execute(() -> finish(result, v, failure));
                    } catch (RejectedExecutionException e) {
                        result.completeExceptionally(e);
                    }
                });
                if (stage instanceof CompletableFuture<?> source) {
                    result.whenComplete((v, failure) -> {
                        if (result.isCancelled()) {
                            source.cancel(true);
                        }
                    });
                }
            } else {
                result.complete(value);
            }
        } catch (Throwable e) {
            result.completeExceptionally(e);
        }
    }

    private static void finish(CompletableFuture<Object> result, Object value, Throwable failure) {
        if (failure != null) {
            result.completeExceptionally(AsyncBoundaryDataFetcher.unwrap(failure));
        } else {
            result.complete(value);
        }
    }
}
