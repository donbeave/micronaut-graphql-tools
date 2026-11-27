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
import io.micronaut.graphql.tools.client.GraphQLRequest;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.GraphQLSession;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import org.reactivestreams.Publisher;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A {@link GraphQLSession} over a client of the registry: it adds the fixed headers and the matching cookies to each request, and
 * stores the cookies of each response before the call completes.
 */
@Internal
public final class DefaultGraphQLSession implements GraphQLSession {

    private final DefaultGraphQLHttpClient client;
    private final String path;
    private final Map<String, String> headers;
    private final CookieJar jar;

    /**
     * Creates a session.
     *
     * @param client  the client that sends the requests
     * @param path    the path of the endpoint on the server
     * @param headers the fixed headers
     * @param clock   the clock that the expiry of a cookie is measured with
     */
    public DefaultGraphQLSession(DefaultGraphQLHttpClient client, String path, Map<String, String> headers, Clock clock) {
        this.client = client;
        this.path = path;
        this.headers = new LinkedHashMap<>(headers);
        this.jar = new CookieJar(clock);
    }

    @Override
    public List<Cookie> getCookies() {
        return jar.snapshot();
    }

    @Override
    public void clearCookies() {
        jar.clear();
    }

    @Override
    public GraphQLSession newSession() {
        return client.newSession(Map.of());
    }

    @Override
    public GraphQLSession newSession(Map<String, String> headers) {
        return client.newSession(headers);
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
        return client.executeObserved(request, session(customizer), this::store);
    }

    @Override
    public <D> GraphQLResponse<D> executeBlocking(GraphQLRequest<D> request) {
        return executeBlocking(request, null);
    }

    @Override
    public <D> GraphQLResponse<D> executeBlocking(GraphQLRequest<D> request, @Nullable Consumer<MutableHttpRequest<?>> customizer) {
        DefaultGraphQLHttpClient.requireBlockingAllowed();
        return DefaultGraphQLHttpClient.await(executeAsync(request, customizer));
    }

    private Consumer<MutableHttpRequest<?>> session(@Nullable Consumer<MutableHttpRequest<?>> customizer) {
        return http -> {
            headers.forEach((name, value) -> http.getHeaders().set(name, value));
            String cookie = jar.header(path);
            if (cookie != null) {
                http.getHeaders().set(HttpHeaders.COOKIE, cookie);
            }
            if (customizer != null) {
                customizer.accept(http);
            }
        };
    }

    private void store(HttpHeaders responseHeaders) {
        List<String> setCookies = responseHeaders.getAll(HttpHeaders.SET_COOKIE);
        if (!setCookies.isEmpty()) {
            jar.store(setCookies, path);
        }
    }
}
