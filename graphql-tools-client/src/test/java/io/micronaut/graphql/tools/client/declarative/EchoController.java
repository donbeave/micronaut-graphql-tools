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
package io.micronaut.graphql.tools.client.declarative;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.serde.ObjectMapper;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A GraphQL server that answers by operation name: the data of an operation is fixed, and {@code Echo} sends back what the request
 * carried.
 */
@Controller("/echo")
public class EchoController {

    public record Seen(String method, String uri, Map<String, Object> body) {
    }

    private final ObjectMapper mapper;
    private final BookClient client;
    private final AtomicInteger flaky = new AtomicInteger();
    private volatile Seen last;

    public EchoController(ObjectMapper mapper, BookClient client) {
        this.mapper = mapper;
        this.client = client;
    }

    public Seen last() {
        return last;
    }

    public void resetFlaky() {
        flaky.set(0);
    }

    public int flakyCalls() {
        return flaky.get();
    }

    @Post("/graphql")
    public HttpResponse<String> post(HttpRequest<?> request, @Body Map<String, Object> body) throws IOException {
        last = new Seen("POST", request.getUri().toString(), body);
        return answer(request, body);
    }

    @Get("/graphql{?query,operationName,variables}")
    public HttpResponse<String> get(HttpRequest<?> request, @QueryValue String query, @Nullable @QueryValue String operationName,
                                    @Nullable @QueryValue String variables) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("operationName", operationName);
        body.put("variables", variables == null ? Map.of() : mapper.readValue(variables, Map.class));
        last = new Seen("GET", request.getUri().toString(), body);
        return answer(request, body);
    }

    @Get("/graphql/plain")
    public String plain() {
        return "plain";
    }

    @Get("/on-event-loop")
    public Mono<String> onEventLoop() {
        try {
            client.book("1");
            return Mono.just("completed");
        } catch (GraphQLClientException e) {
            return Mono.just(e.getCode());
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> answer(HttpRequest<?> request, Map<String, Object> body) throws IOException {
        Map<String, Object> variables = body.get("variables") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        String data = switch ((String) body.get("operationName")) {
            case "Book" -> "{\"data\":{\"book\":{\"title\":\"T" + variables.get("id") + "\",\"pages\":3,\"id\":" + variables.get("id") + "}}}";
            case "Missing" -> "{\"data\":{\"book\":null}}";
            case "Fail" -> "{\"data\":{\"book\":{\"title\":\"partial\",\"pages\":1,\"id\":1}},\"errors\":[{\"message\":\"boom\"}]}";
            case "Touch" -> "{\"data\":{\"touch\":true}}";
            case "Pair" -> "{\"data\":{\"a\":1,\"b\":2}}";
            case "Echo" -> {
                Map<String, List<String>> headers = new LinkedHashMap<>();
                request.getHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values));
                Map<String, String> cookies = new LinkedHashMap<>();
                request.getCookies().getAll().forEach(cookie -> cookies.put(cookie.getName(), cookie.getValue()));
                yield "{\"data\":{\"echo\":" + mapper.writeValueAsString(Map.of("variables", variables, "headers", headers, "cookies", cookies)) + "}}";
            }
            case "Flaky" -> flaky.incrementAndGet() < 3 ? null : "{\"data\":{\"book\":{\"title\":\"ok\",\"pages\":1,\"id\":1}}}";
            default -> "{\"errors\":[{\"message\":\"unknown operation\"}]}";
        };
        if (data == null) {
            return HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).body("<html>busy</html>").contentType("text/html");
        }
        return HttpResponse.ok(data).contentType("application/graphql-response+json");
    }
}
