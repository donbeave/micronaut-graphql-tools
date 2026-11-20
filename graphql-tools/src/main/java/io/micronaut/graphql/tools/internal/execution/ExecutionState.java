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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;

import java.util.concurrent.Executor;

/**
 * What the library knows about one execution (one HTTP request, one WebSocket operation): the propagated context
 * captured on the thread that received it, the transport, the request, the response and the connection attributes. It
 * is the one entry the library puts in the {@link GraphQLContext}, under a key of its own.
 */
@Internal
public final class ExecutionState {

    private static final Object KEY = new Object() {
        @Override
        public String toString() {
            return "micronaut-graphql-tools.execution-state";
        }
    };

    private final PropagatedContext propagatedContext;
    private final ExecutionTransport transport;
    @Nullable
    private final HttpRequest<?> request;
    @Nullable
    private final MutableHttpResponse<?> response;
    private final ConvertibleValues<Object> connectionAttributes;
    @Nullable
    private final Executor requestExecutor;

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
     */
    public ExecutionState(PropagatedContext propagatedContext, ExecutionTransport transport, @Nullable HttpRequest<?> request,
                   @Nullable MutableHttpResponse<?> response, ConvertibleValues<Object> connectionAttributes,
                   @Nullable Executor requestExecutor) {
        this.propagatedContext = propagatedContext;
        this.transport = transport;
        this.request = request;
        this.response = response;
        this.connectionAttributes = connectionAttributes;
        this.requestExecutor = requestExecutor;
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
     * @return the propagated context captured when the operation arrived, with the request in it
     */
    public PropagatedContext propagatedContext() {
        return propagatedContext;
    }

    /**
     * @return the executor the operation hopped to, which the async boundary hands the children of an async field back
     * to when the field completes on an event-loop thread; null when the operation runs on the calling thread
     */
    @Nullable
    public Executor requestExecutor() {
        return requestExecutor;
    }

    /**
     * @return how the operation arrived
     */
    public ExecutionTransport transport() {
        return transport;
    }

    /**
     * @return the HTTP request, or the upgrade request of a WebSocket connection; null for a call that has none
     */
    @Nullable
    public HttpRequest<?> request() {
        return request;
    }

    /**
     * @return the HTTP response, or null when the transport has none (WebSocket)
     */
    @Nullable
    public MutableHttpResponse<?> response() {
        return response;
    }

    /**
     * @return the attributes of the WebSocket connection; empty over HTTP
     */
    public ConvertibleValues<Object> connectionAttributes() {
        return connectionAttributes;
    }

}
