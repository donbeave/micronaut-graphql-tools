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

import graphql.execution.AbortExecutionException;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import reactor.core.scheduler.NonBlocking;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;

/**
 * The continuation guard of a field that returns its value later. graphql-java resolves the children of such a field on
 * whichever thread completes the future, which can be an event-loop thread of the HTTP client, and without the
 * request's propagated context. The fetcher returns a new future that the library completes: inside the context that
 * the execution captured and, when the source completed on a Netty event-loop thread (a
 * {@link NonBlocking} thread, which Micronaut's event-loop threads are while
 * {@code micronaut.netty.default-thread-factory-reactor-non-blocking} is {@code true}, its default), on the executor
 * that runs the request. An exception is unwrapped from {@link CompletionException} and {@link ExecutionException}
 * first, so that the error handling sees its real type.
 *
 * <p>A future that is already done, without a failure, on a thread that holds the context is returned as it is: it
 * allocates nothing. Otherwise a call allocates one object, which is both the returned future and the callback of the
 * source. A result that is not a stage (null, or a {@code DataFetcherResult} of an argument that did not convert) and an
 * execution that the library's invocation did not start pass through.</p>
 */
@Internal
public final class AsyncBoundaryDataFetcher implements DataFetcher<Object> {

    private final DataFetcher<Object> delegate;

    /**
     * @param delegate the fetcher that returns the stage: the method fetcher, or an adapter around it
     */
    public AsyncBoundaryDataFetcher(DataFetcher<Object> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        Object result = delegate.get(environment);
        if (!(result instanceof CompletionStage<?> stage)) {
            return result;
        }
        ExecutionState state = ExecutionState.of(environment.getGraphQlContext());
        if (state == null) {
            return result;
        }
        if (result instanceof CompletableFuture<?> future && doneWithoutFailure(future)
                && PropagatedContext.getOrEmpty() == state.propagatedContext()) {
            return result;
        }
        Boundary boundary = new Boundary(state, stage);
        state.awaitingResult();
        stage.whenComplete(boundary);
        return boundary;
    }

    // CompletableFuture.completedStage, failedStage and minimalCompletionStage return a CompletableFuture that refuses to be
    // inspected: such a stage is not done as far as the fast path knows, and the boundary completes from its callback
    private static boolean doneWithoutFailure(CompletableFuture<?> future) {
        try {
            return future.isDone() && !future.isCompletedExceptionally();
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    /**
     * @param failure what a stage completed with
     * @return the failure without the {@link CompletionException} and {@link ExecutionException} layers around it
     */
    public static Throwable unwrap(Throwable failure) {
        Throwable result = failure;
        while ((result instanceof CompletionException || result instanceof ExecutionException) && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    /** The future that graphql-java continues on, and the callback of the source. */
    private static final class Boundary extends CompletableFuture<Object> implements BiConsumer<Object, Throwable> {

        private final ExecutionState state;
        private final CompletionStage<?> source;

        Boundary(ExecutionState state, CompletionStage<?> source) {
            this.state = state;
            this.source = source;
        }

        /** Cancelling the returned future (graphql-java does on a timeout) cancels the source, as the adapter of a publisher does. */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled && source instanceof CompletableFuture<?> future) {
                try {
                    future.cancel(mayInterruptIfRunning);
                } catch (UnsupportedOperationException e) {
                    // a minimal stage cannot be cancelled: there is nothing to cancel
                }
            }
            return cancelled;
        }

        @Override
        public void accept(Object value, Throwable failure) {
            Executor executor = state.requestExecutor();
            if (executor != null && Thread.currentThread() instanceof NonBlocking) {
                try {
                    executor.execute(() -> completeInContext(value, failure));
                } catch (RejectedExecutionException e) {
                    completeInContext(null, e);
                }
            } else {
                completeInContext(value, failure);
            }
        }

        private void completeInContext(Object value, Throwable failure) {
            PropagatedContext captured = state.propagatedContext();
            if (PropagatedContext.getOrEmpty() == captured) {
                finish(value, failure);
                return;
            }
            try (PropagatedContext.Scope ignored = captured.propagate()) {
                finish(value, failure);
            }
        }

        private void finish(Object value, Throwable failure) {
            if (state.isCancelled()) {
                completeExceptionally(new AbortExecutionException("The execution was cancelled"));
            } else if (failure != null) {
                completeExceptionally(unwrap(failure));
            } else {
                complete(value);
            }
        }
    }
}
