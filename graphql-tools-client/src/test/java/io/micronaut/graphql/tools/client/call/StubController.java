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
package io.micronaut.graphql.tools.client.call;

import io.micronaut.graphql.tools.client.GraphQLHttpClient;
import io.micronaut.graphql.tools.client.GraphQLRequest;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

/**
 * A server that answers what the request asks for: the {@code X-Status} and {@code X-Kind} headers pick the status and the body,
 * so the classifier sees statuses that a real GraphQL server never sends.
 */
@Controller
public class StubController {

    public record Seen(String method, String uri, Map<String, List<String>> headers, Map<String, List<String>> parameters, String body) {
    }

    private static final Map<String, String[]> KINDS = Map.ofEntries(
            Map.entry("data", new String[]{"application/graphql-response+json", "{\"data\":{\"a\":1}}"}),
            Map.entry("errors", new String[]{"application/graphql-response+json", "{\"errors\":[{\"message\":\"boom\"}]}"}),
            Map.entry("partial", new String[]{"application/json", "{\"data\":{\"a\":null},\"errors\":[{\"message\":\"p\",\"path\":[\"a\"]}]}"}),
            Map.entry("null-data", new String[]{"application/json", "{\"data\":null}"}),
            Map.entry("empty-object", new String[]{"application/json", "{}"}),
            Map.entry("extensions-only", new String[]{"application/json", "{\"extensions\":{\"x\":1}}"}),
            Map.entry("json-message", new String[]{"application/json", "{\"message\":\"Not Found\"}"}),
            Map.entry("array", new String[]{"application/json", "[]"}),
            Map.entry("html", new String[]{"text/html", "<html>oops</html>"}),
            Map.entry("empty", new String[]{"application/json", ""}),
            Map.entry("truncated", new String[]{"application/json", "{\"data\":"}),
            Map.entry("wrong-shape", new String[]{"application/json", "{\"data\":[1,2]}"}));

    public static final List<String> KIND_NAMES = List.copyOf(KINDS.keySet());

    private final GraphQLHttpClient loop;
    private volatile Seen last;
    private volatile CompletableFuture<Boolean> gate = CompletableFuture.completedFuture(true);

    public StubController(@GraphQLClient("stub") GraphQLHttpClient loop) {
        this.loop = loop;
    }

    public Seen last() {
        return last;
    }

    /**
     * Holds the answers of {@code /gated} until the returned future completes, so a test can attach a continuation to a call before its
     * response can arrive.
     *
     * @return the gate to open
     */
    public CompletableFuture<Boolean> closeGate() {
        CompletableFuture<Boolean> closed = new CompletableFuture<>();
        gate = closed;
        return closed;
    }

    @Post(uris = {"/graphql", "/api/graphql"})
    public HttpResponse<String> post(HttpRequest<String> request, @Body String body) {
        return answer(request, body);
    }

    @Get(uris = {"/graphql{?query}", "/api/graphql{?query}"})
    public HttpResponse<String> get(HttpRequest<?> request, @QueryValue String query) {
        return answer(request, null);
    }

    @Post("/slow")
    public Publisher<HttpResponse<String>> slow() {
        return Mono.delay(java.time.Duration.ofSeconds(2)).map(ignored -> HttpResponse.ok("{\"data\":{\"a\":1}}").contentType(MediaType.APPLICATION_JSON_TYPE));
    }

    @Post("/gated")
    public Publisher<HttpResponse<String>> gated() {
        return Mono.fromFuture(gate).map(ignored -> HttpResponse.ok("{\"data\":{\"a\":1}}").contentType(MediaType.APPLICATION_JSON_TYPE));
    }

    @Get("/blocks-on-the-event-loop")
    public Mono<String> blocksOnTheEventLoop() {
        try {
            loop.executeBlocking(GraphQLRequest.builder().document("{ a }").build());
            return Mono.just("completed");
        } catch (GraphQLClientException e) {
            return Mono.just(e.getCode());
        }
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    @Get("/blocks-on-a-worker")
    public String blocksOnAWorker() {
        return String.valueOf(loop.executeBlocking(GraphQLRequest.builder().document("{ a }").build()).data());
    }

    private HttpResponse<String> answer(HttpRequest<?> request, String body) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        request.getHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values));
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        request.getParameters().forEach(parameters::put);
        last = new Seen(request.getMethodName(), request.getUri().toString(), headers, parameters, body);
        int status = Integer.parseInt(request.getHeaders().get("X-Status", String.class).orElse("200"));
        String[] kind = KINDS.get(request.getHeaders().get("X-Kind", String.class).orElse("data"));
        MutableHttpResponse<String> response = HttpResponse.ok(kind[1]);
        response.status(status, "status " + status);
        response.contentType(kind[0]);
        request.getHeaders().getAll("X-Set-Cookie").forEach(cookie -> response.getHeaders().add("Set-Cookie", cookie));
        return response;
    }
}
