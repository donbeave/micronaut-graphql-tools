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
package io.micronaut.graphql.tools.internal.kotlin;

import io.micronaut.aop.util.CompletableFutureContinuation;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Executable;
import io.micronaut.core.util.KotlinUtils;
import io.micronaut.graphql.tools.internal.execution.SuspendInvoker;
import io.micronaut.http.bind.binders.HttpCoroutineContextFactory;
import jakarta.inject.Singleton;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.JobKt;
import kotlinx.coroutines.ThreadContextElement;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Starts the coroutine of a Kotlin {@code suspend} resolver method. It builds the continuation the way Micronaut does for
 * a {@code suspend} controller method: a continuation from Micronaut's {@code CompletableFutureContinuation}, over a coroutine
 * context that holds the current propagated context and the contexts of the {@code HttpCoroutineContextFactory} beans. The
 * coroutine starts on the calling thread and resumes on the thread of its suspension point: the context holds
 * {@code Dispatchers.Unconfined}, which moves no work to another thread but makes the coroutine install the propagated
 * context on whichever thread resumes it (a coroutine without an interceptor is resumed without that). The async boundary
 * then restores the propagated context and leaves the event loop for the children of the field. The context also holds a job
 * of its own, which cancelling the returned future cancels, so that a coroutine does not outlive a cancelled field.
 *
 * <p>The bean needs the Kotlin standard library and kotlinx-coroutines-core. Without them it does not exist, and binding a
 * {@code suspend} method fails the startup with {@code suspend-without-coroutines}.</p>
 */
@Internal
@Singleton
@Requires(classes = {Continuation.class, ThreadContextElement.class})
final class KotlinSuspendInvoker implements SuspendInvoker {

    private final List<HttpCoroutineContextFactory<?>> contextFactories;

    KotlinSuspendInvoker(List<HttpCoroutineContextFactory<?>> contextFactories) {
        this.contextFactories = contextFactories;
    }

    @Override
    public CompletionStage<Object> invoke(Executable<Object, ?> executable, Object target, Object[] arguments) {
        CompletableJob job = JobKt.Job(null);
        CoroutineContext context = CoroutineContexts.create(job, contextFactories);

        CompletableFutureContinuation continuation = new CompletableFutureContinuation(new ContextContinuation(context));
        SuspendFuture future = new SuspendFuture(job);
        continuation.setCompletableFuture(future);
        arguments[arguments.length - 1] = continuation;
        Object result = executable.invoke(target, arguments);
        if (result != KotlinUtils.COROUTINE_SUSPENDED) {
            future.complete(result);
        }
        return future;
    }

    /**
     * The parent of the continuation: it only supplies the coroutine context.
     *
     * @param context the coroutine context of the call
     */
    private record ContextContinuation(CoroutineContext context) implements Continuation<Object> {

        @Override
        public CoroutineContext getContext() {
            return context;
        }

        @Override
        public void resumeWith(Object result) {
            // the continuation of the method completes the future, nothing resumes after it
        }
    }

    /** The result of the coroutine. Cancelling it cancels the job, which cancels the coroutine at its next suspension point. */
    private static final class SuspendFuture extends CompletableFuture<Object> {

        private final CompletableJob job;

        SuspendFuture(CompletableJob job) {
            this.job = job;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                job.cancel((CancellationException) null);
            }
            return cancelled;
        }
    }
}
