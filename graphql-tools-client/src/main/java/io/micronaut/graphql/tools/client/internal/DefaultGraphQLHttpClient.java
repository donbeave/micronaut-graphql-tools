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
package io.micronaut.graphql.tools.client.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.GraphQLClientAttributes;
import io.micronaut.graphql.tools.client.GraphQLHttpClient;
import io.micronaut.graphql.tools.client.GraphQLMediaTypes;
import io.micronaut.graphql.tools.client.GraphQLRequest;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.OperationType;
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException;
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException;
import io.micronaut.graphql.tools.client.internal.serde.GraphQLCodec;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import org.reactivestreams.Publisher;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Sends the requests of {@link GraphQLHttpClient} over a Micronaut HTTP client that the registry owns.
 */
@Internal
public final class DefaultGraphQLHttpClient implements GraphQLHttpClient {

    private static final Argument<byte[]> BYTES = Argument.of(byte[].class);

    private final HttpClient transport;
    private final GraphQLCodec codec;
    private final String endpoint;

    /**
     * Creates a client.
     *
     * @param transport the HTTP client of the registry, which this client never closes
     * @param codec     the codec of requests and responses
     * @param endpoint  the path of every request, relative to the path that the transport was created with; empty for that path itself
     */
    public DefaultGraphQLHttpClient(HttpClient transport, GraphQLCodec codec, String endpoint) {
        this.transport = transport;
        this.codec = codec;
        this.endpoint = endpoint;
    }

    @Override
    public <D> Publisher<GraphQLResponse<D>> execute(GraphQLRequest<D> request) {
        return execute(request, null);
    }

    @Override
    public <D> Publisher<GraphQLResponse<D>> execute(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer) {
        return Publishers.fromCompletableFuture(() -> executeAsync(request, customizer));
    }

    @Override
    public <D> CompletableFuture<GraphQLResponse<D>> executeAsync(GraphQLRequest<D> request) {
        return executeAsync(request, null);
    }

    @Override
    public <D> CompletableFuture<GraphQLResponse<D>> executeAsync(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer) {
        return call(request, customizer, false);
    }

    @Override
    public <D> GraphQLResponse<D> executeBlocking(GraphQLRequest<D> request) {
        return executeBlocking(request, null);
    }

    @Override
    public <D> GraphQLResponse<D> executeBlocking(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer) {
        requireBlockingAllowed();
        return await(call(request, customizer, false));
    }

    /**
     * Sends a request whose {@code data} is an object with one root field, and returns the value of that field as the data.
     *
     * @param request    the request, whose data type is the type of the root field
     * @param customizer changes the HTTP request, or {@code null} for none
     * @param <D>        the type of the root field
     * @return a future of the response
     */
    public <D> CompletableFuture<GraphQLResponse<D>> executeUnwrapped(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer) {
        return call(request, customizer, true);
    }

    private static void requireBlockingAllowed() {
        if (EventLoops.isEventLoopThread()) {
            throw new GraphQLClientException(GraphQLClientException.BLOCKING_ON_EVENT_LOOP,
                    "A blocking call on an event loop thread would stall it; use execute or executeAsync, or call from another thread");
        }
    }

    private static <D> GraphQLResponse<D> await(CompletableFuture<GraphQLResponse<D>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new GraphQLTransportException("The call was interrupted", GraphQLTransportException.NO_STATUS, null, false, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new GraphQLTransportException("The call failed: " + cause, GraphQLTransportException.NO_STATUS, null, false, cause);
        }
    }

    private <D> CompletableFuture<GraphQLResponse<D>> call(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer, boolean unwrap) {
        try {
            MutableHttpRequest<?> http = build(request);
            if (customizer != null) {
                customizer.accept(http);
            }
            Call<D> call = new Call<>(codec, request, unwrap);
            transport.exchange(http, BYTES, BYTES).subscribe(call);
            return call;
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private MutableHttpRequest<?> build(GraphQLRequest<?> request) {
        OperationType type = request.getOperationType();
        if (type == OperationType.SUBSCRIPTION) {
            throw new IllegalArgumentException("A subscription cannot be sent as an HTTP request and response; it needs the subscription transport");
        }
        MutableHttpRequest<?> http;
        if (request.getHttpMethod() == HttpMethod.GET) {
            http = HttpRequest.GET(endpoint + queryString(request));
        } else {
            http = HttpRequest.POST(endpoint, codec.encode(request)).contentType(MediaType.APPLICATION_JSON_TYPE);
        }
        http.getHeaders().set(HttpHeaders.ACCEPT, GraphQLMediaTypes.ACCEPT);
        if (request.getOperationName() != null) {
            http.setAttribute(GraphQLClientAttributes.OPERATION_NAME, request.getOperationName());
        }
        http.setAttribute(GraphQLClientAttributes.OPERATION_TYPE, type);
        return http;
    }

    private String queryString(GraphQLRequest<?> request) {
        StringBuilder query = new StringBuilder(request.getDocument().length() + 32).append("?query=");
        encode(query, request.getDocument());
        if (request.getOperationName() != null) {
            encode(query.append("&operationName="), request.getOperationName());
        }
        append(query, "&variables=", request.getVariables());
        append(query, "&extensions=", request.getExtensions());
        return query.toString();
    }

    private void append(StringBuilder query, String name, Map<String, Object> value) {
        if (!value.isEmpty()) {
            encode(query.append(name), codec.encodeValue(value));
        }
    }

    private static void encode(StringBuilder query, String value) {
        query.append(URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
    }
}
