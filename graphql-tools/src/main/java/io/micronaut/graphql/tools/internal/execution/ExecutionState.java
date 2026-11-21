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

import graphql.GraphQLContext;
import graphql.ExecutionInput;
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest;
import io.micronaut.graphql.tools.execution.GraphQLTransport;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.graphql.tools.internal.execution.disconnect.DisconnectWatcher;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * What the library knows about one execution (one HTTP request, one WebSocket operation): the propagated context
 * captured on the thread that received it, the transport, the request, the response and the connection attributes. It
 * is the one entry the library puts in the {@link GraphQLContext}, under a key of its own.
 */
@Internal
public final class ExecutionState implements GraphQLExecutionRequest {

    private static final Object KEY = new Object() {
        @Override
        public String toString() {
            return "micronaut-graphql-tools.execution-state";
        }
    };

    private volatile PropagatedContext propagatedContext;
    private final GraphQLTransport transport;
    @Nullable
    private final HttpRequest<?> request;
    @Nullable
    private final MutableHttpResponse<?> response;
    private final ConvertibleValues<Object> connectionAttributes;
    @Nullable
    private final Executor requestExecutor;
    private volatile boolean cancelled;
    private volatile boolean subscription;
    @Nullable
    private DisconnectWatcher disconnectWatcher;
    private boolean watching;
    @Nullable
    private Runnable unwatch;
    @Nullable
    private final String customizer;
    private ExecutionInput executionInput;

    /**
     * Public for the benchmarks of this repository, which build an execution without the invocation; the library's
     * invocation is the only other caller.
     *
     * @param propagatedContext    the context captured when the operation arrived
     * @param transport            how the operation arrived
     * @param request              the request, or null
     * @param response             the response, or null
     * @param connectionAttributes the attributes of a WebSocket connection, else empty
     * @param requestExecutor      the executor of the operation, or null when it runs on the calling thread
     * @param customizer           the class name of the application's customizer, or null when there is none
     */
    public ExecutionState(PropagatedContext propagatedContext, GraphQLTransport transport, @Nullable HttpRequest<?> request,
                          @Nullable MutableHttpResponse<?> response, ConvertibleValues<Object> connectionAttributes,
                          @Nullable Executor requestExecutor, @Nullable String customizer) {
        this.propagatedContext = propagatedContext;
        this.transport = transport;
        this.request = request;
        this.response = response;
        this.connectionAttributes = connectionAttributes;
        this.requestExecutor = requestExecutor;
        this.customizer = customizer;
    }

    /**
     * @param context the GraphQL context of an execution
     * @return the state of that execution, or null when the library's invocation did not start it
     */
    @Nullable
    public static ExecutionState of(GraphQLContext context) {
        return context.get(KEY);
    }

    /**
     * @param context the GraphQL context of the execution that this state belongs to
     */
    public void storeIn(GraphQLContext context) {
        context.put(KEY, this);
    }

    boolean isStoredIn(GraphQLContext context) {
        return context.get(KEY) == this;
    }

    /**
     * @return the propagated context captured when the operation arrived, with the request in it and the elements that
     * {@link #addToPropagatedContext} added since
     */
    public PropagatedContext propagatedContext() {
        return propagatedContext;
    }

    /**
     * Adds an element to the propagated context of the rest of the execution: the continuations of async fields run inside
     * a context that holds it. This does not touch the current thread: the caller installs the returned context with
     * {@link PropagatedContext#propagate()}, and the thread then holds the very object that the continuations get, which
     * is what keeps an async field that is already done on the fast path of the async boundary.
     *
     * @param element what the continuations of the execution run with, for example the current span of a request
     * @return the propagated context of the execution, with the element in it
     */
    public PropagatedContext addToPropagatedContext(PropagatedContextElement element) {
        PropagatedContext extended = propagatedContext.plus(element);
        propagatedContext = extended;
        return extended;
    }

    /**
     * @return the executor the operation hopped to, which the async boundary hands the children of an async field back
     * to when the field completes on an event-loop thread; null when the operation runs on the calling thread
     */
    @Nullable
    public Executor requestExecutor() {
        return requestExecutor;
    }

    @Override
    public GraphQLTransport transport() {
        return transport;
    }

    @Override
    @Nullable
    public HttpRequest<?> httpRequest() {
        return request;
    }

    @Override
    public Optional<MutableHttpResponse<?>> httpResponse() {
        return Optional.ofNullable(response);
    }

    /**
     * @return the HTTP response, or null when the transport has none (WebSocket)
     */
    @Nullable
    MutableHttpResponse<?> response() {
        return response;
    }

    @Override
    public ConvertibleValues<Object> connectionAttributes() {
        return connectionAttributes;
    }

    /**
     * Marks the execution cancelled: its client went away (an HTTP disconnect, a WebSocket {@code complete} or close). Cancellation is
     * cooperative: a hop task that has not started is skipped, a later async boundary completes with an abort and a
     * stream is cancelled, but synchronous code that is running is never interrupted.
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * @return whether {@link #cancel()} was called
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Marks the execution as a subscription operation: the loaders that it creates from now on have caching off.
     */
    void markSubscription() {
        subscription = true;
    }

    /**
     * @return whether the operation is a subscription, known once its root field is fetched
     */
    boolean isSubscription() {
        return subscription;
    }

    void disconnectWatcher(DisconnectWatcher watcher) {
        this.disconnectWatcher = watcher;
    }

    /**
     * Called when a field returned a stage that is still pending, which is the time the operation waits and the client can
     * go away. The first call starts to watch the connection of the request, when there is a watcher; a synchronous
     * operation never pays for it.
     */
    void awaitingResult() {
        if (disconnectWatcher == null) {
            return;
        }
        synchronized (this) {
            if (watching || cancelled || disconnectWatcher == null) {
                return;
            }
            watching = true;
            unwatch = disconnectWatcher.watch(request, this::cancel);
        }
    }

    /**
     * Called when the execution has its result: the connection is not watched any more.
     */
    void finished() {
        if (disconnectWatcher == null) {
            return;
        }
        Runnable stop;
        synchronized (this) {
            stop = unwatch;
            unwatch = null;
            disconnectWatcher = null;
        }
        if (stop != null) {
            stop.run();
        }
    }

    @Override
    public ExecutionInput executionInput() {
        return executionInput;
    }

    void executionInput(ExecutionInput input) {
        this.executionInput = input;
    }

    /**
     * @return the class name of the application's {@code GraphQLExecutionInputCustomizer}, or null when there is none
     */
    @Nullable
    public String customizer() {
        return customizer;
    }

}
