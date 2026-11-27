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
import io.micronaut.graphql.tools.client.internal.serde.OperationRequest;
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
        return call(request, customizer);
    }

    @Override
    public <D> GraphQLResponse<D> executeBlocking(GraphQLRequest<D> request) {
        return executeBlocking(request, null);
    }

    @Override
    public <D> GraphQLResponse<D> executeBlocking(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer) {
        requireBlockingAllowed();
        return await(call(request, customizer));
    }

    /**
     * Sends the call of a declarative client.
     *
     * @param request    the call
     * @param customizer changes the HTTP request, or {@code null} for none
     * @param unwrap     whether the {@code data} is an object with one root field and the result is the value of that field
     * @param outcome    what the result is
     * @param <R>        the type of the result
     * @return a future of the result, which holds every failure, including a request that cannot be sent
     */
    public <R> CompletableFuture<R> executeOperation(OperationRequest request, @Nullable Consumer<MutableHttpRequest<?>> customizer,
                                                     boolean unwrap, Outcome outcome) {
        try {
            return send(build(request), customizer, request.responseType(), unwrap, outcome);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Sends the call of a declarative client and waits for the result.
     *
     * @param request    the call
     * @param customizer changes the HTTP request, or {@code null} for none
     * @param unwrap     whether the {@code data} is an object with one root field and the result is the value of that field
     * @param outcome    what the result is
     * @param <R>        the type of the result
     * @return the result
     */
    public <R> R executeOperationBlocking(OperationRequest request, @Nullable Consumer<MutableHttpRequest<?>> customizer,
                                          boolean unwrap, Outcome outcome) {
        requireBlockingAllowed();
        return await(this.<R>executeOperation(request, customizer, unwrap, outcome));
    }

    private static void requireBlockingAllowed() {
        if (EventLoops.isEventLoopThread()) {
            throw new GraphQLClientException(GraphQLClientException.BLOCKING_ON_EVENT_LOOP,
                    "A blocking call on an event loop thread would stall it; use execute or executeAsync, or call from another thread");
        }
    }

    private static <R> R await(CompletableFuture<R> future) {
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

    private <D> CompletableFuture<GraphQLResponse<D>> call(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer) {
        try {
            return send(build(request), customizer, codec.responseType(request.getDataType(), false), false, Outcome.RESPONSE);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private <R> CompletableFuture<R> send(MutableHttpRequest<?> http, @Nullable Consumer<MutableHttpRequest<?>> customizer,
                                          Argument<?> responseType, boolean unwrap, Outcome outcome) {
        if (customizer != null) {
            customizer.accept(http);
        }
        Call<R> call = new Call<>(codec, responseType, unwrap, outcome);
        transport.exchange(http, BYTES, BYTES).subscribe(call);
        return call;
    }

    private MutableHttpRequest<?> build(GraphQLRequest<?> request) {
        OperationType type = request.getOperationType();
        requireHttp(type);
        MutableHttpRequest<?> http;
        if (request.getHttpMethod() == HttpMethod.GET) {
            http = HttpRequest.GET(endpoint + queryString(request.getDocument(), request.getOperationName(), json(request.getVariables()),
                    json(request.getExtensions())));
        } else {
            http = HttpRequest.POST(endpoint, codec.encode(request)).contentType(MediaType.APPLICATION_JSON_TYPE);
        }
        return decorate(http, request.getOperationName(), type);
    }

    private MutableHttpRequest<?> build(OperationRequest request) {
        requireHttp(request.operationType());
        MutableHttpRequest<?> http;
        if (request.httpMethod() == HttpMethod.GET) {
            http = HttpRequest.GET(endpoint + queryString(request.document(), request.operationName(),
                    request.variableNames().length == 0 ? null : codec.encodeVariables(request), null));
        } else {
            http = HttpRequest.POST(endpoint, codec.encode(request)).contentType(MediaType.APPLICATION_JSON_TYPE);
        }
        return decorate(http, request.operationName(), request.operationType());
    }

    private static void requireHttp(OperationType type) {
        if (type == OperationType.SUBSCRIPTION) {
            throw new IllegalArgumentException("A subscription cannot be sent as an HTTP request and response; it needs the subscription transport");
        }
    }

    private static MutableHttpRequest<?> decorate(MutableHttpRequest<?> http, @Nullable String operationName, OperationType type) {
        http.getHeaders().set(HttpHeaders.ACCEPT, GraphQLMediaTypes.ACCEPT);
        if (operationName != null) {
            http.setAttribute(GraphQLClientAttributes.OPERATION_NAME, operationName);
        }
        http.setAttribute(GraphQLClientAttributes.OPERATION_TYPE, type);
        return http;
    }

    private String json(Map<String, Object> value) {
        return value.isEmpty() ? null : codec.encodeValue(value);
    }

    private static String queryString(String document, @Nullable String operationName, @Nullable String variables, @Nullable String extensions) {
        StringBuilder query = new StringBuilder(document.length() + 32).append("?query=");
        encode(query, document);
        if (operationName != null) {
            encode(query.append("&operationName="), operationName);
        }
        if (variables != null) {
            encode(query.append("&variables="), variables);
        }
        if (extensions != null) {
            encode(query.append("&extensions="), extensions);
        }
        return query.toString();
    }

    private static void encode(StringBuilder query, String value) {
        query.append(URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
    }
}
