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
package io.micronaut.graphql.tools.client;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MutableHttpRequest;
import org.reactivestreams.Publisher;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The low-level GraphQL client: it sends a {@link GraphQLRequest} to a GraphQL server over HTTP and returns the
 * {@link GraphQLResponse}. Inject it with {@code @Inject @GraphQLClient("service-id") GraphQLHttpClient client}. The service id
 * reads {@code micronaut.http.services.<id>.*}, so discovery, load balancing, filters, timeouts and TLS are those of the Micronaut
 * HTTP client.
 *
 * <p>A request is sent with POST, a JSON body and an {@code Accept} header that prefers
 * {@code application/graphql-response+json}, or with GET for a query that asks for it. The response is classified by its status
 * and body: a 2xx response with a GraphQL body and a 4xx or 5xx response with a well-formed GraphQL body are a
 * {@link GraphQLResponse}; any other response, and every 404, is a {@link io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException}, as are a timeout and a
 * connection error.</p>
 *
 * <p>The reactive and the {@link CompletableFuture} variants complete on the event loop of the HTTP client, so a continuation must
 * not block. The blocking variant fails with {@link io.micronaut.graphql.tools.client.exceptions.GraphQLClientException#BLOCKING_ON_EVENT_LOOP} on an event loop thread.</p>
 *
 * <p>The variants that return a {@link Publisher} or a {@link CompletableFuture} report every failure through it. A
 * {@link Publisher} is cold: it sends the request on each subscription, and it emits one response.</p>
 *
 * @since 1.0.0
 */
public interface GraphQLHttpClient {

    /**
     * Sends a request.
     *
     * @param request the request
     * @param <D>     the type of the {@code data}
     * @return a publisher of the response
     */
    <D> Publisher<GraphQLResponse<D>> execute(GraphQLRequest<D> request);

    /**
     * Sends a request that the customizer changes before it is sent, for example to add a header.
     *
     * @param request    the request
     * @param customizer changes the HTTP request, or {@code null} for none
     * @param <D>        the type of the {@code data}
     * @return a publisher of the response
     */
    <D> Publisher<GraphQLResponse<D>> execute(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer);

    /**
     * Sends a request.
     *
     * @param request the request
     * @param <D>     the type of the {@code data}
     * @return a future of the response
     */
    <D> CompletableFuture<GraphQLResponse<D>> executeAsync(GraphQLRequest<D> request);

    /**
     * Sends a request that the customizer changes before it is sent.
     *
     * @param request    the request
     * @param customizer changes the HTTP request, or {@code null} for none
     * @param <D>        the type of the {@code data}
     * @return a future of the response; cancelling it cancels the call
     */
    <D> CompletableFuture<GraphQLResponse<D>> executeAsync(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer);

    /**
     * Sends a request and waits for the response.
     *
     * @param request the request
     * @param <D>     the type of the {@code data}
     * @return the response
     * @throws io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException when the call fails below GraphQL
     * @throws io.micronaut.graphql.tools.client.exceptions.GraphQLClientException when the response cannot be decoded, or the call is made on an event loop thread
     */
    <D> GraphQLResponse<D> executeBlocking(GraphQLRequest<D> request);

    /**
     * Sends a request that the customizer changes before it is sent, and waits for the response.
     *
     * @param request    the request
     * @param customizer changes the HTTP request, or {@code null} for none
     * @param <D>        the type of the {@code data}
     * @return the response
     * @throws io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException when the call fails below GraphQL
     * @throws io.micronaut.graphql.tools.client.exceptions.GraphQLClientException when the response cannot be decoded, or the call is made on an event loop thread
     */
    <D> GraphQLResponse<D> executeBlocking(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer);

    /**
     * Starts a session: a {@link GraphQLSession} that keeps the cookies that the responses set and replays them on its next calls. This
     * client keeps no cookies, because every caller shares it.
     *
     * @return a new session with an empty cookie jar
     */
    GraphQLSession newSession();

    /**
     * Starts a session that also sends fixed headers on every call.
     *
     * @param headers the headers to send, by name
     * @return a new session with an empty cookie jar
     */
    GraphQLSession newSession(Map<String, String> headers);
}
