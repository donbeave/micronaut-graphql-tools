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
package io.micronaut.graphql.tools.tck;

import io.micronaut.context.ApplicationContext;
import io.micronaut.json.JsonMapper;
import io.micronaut.runtime.server.EmbeddedServer;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static io.micronaut.graphql.tools.tck.Wire.expect;
import static io.micronaut.graphql.tools.tck.Wire.path;

/**
 * The end-to-end checks of the HTTP request and response in a resolver (the scenario {@code http-access}): a real client sets
 * request headers and reads response headers and cookies over HTTP, and sends headers on a WebSocket upgrade request. A failed
 * expectation is an {@link AssertionError} that names it.
 */
public final class HttpAccessTck {

    private static final List<String> CHECKS = List.of(
            "response-cookie-and-header",
            "request-header-over-http",
            "request-header-over-websocket",
            "optional-response-over-websocket",
            "plain-response-over-websocket");

    private HttpAccessTck() {
    }

    /**
     * @return the names of the checks
     */
    public static List<String> checks() {
        return CHECKS;
    }

    /**
     * Runs one check against a fresh application.
     *
     * @param check one of {@link #checks()}
     */
    public static void run(String check) {
        if (!CHECKS.contains(check)) {
            throw new IllegalArgumentException("No such check: " + check);
        }
        try (ApplicationContext context = ApplicationContext.builder().properties(Tck.applicationProperties("http-access")).start();
             HttpClient client = HttpClient.newHttpClient()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            JsonMapper json = context.getBean(JsonMapper.class);
            String authority = server.getHost() + ":" + server.getPort();
            Map<String, String> tck = Map.of("X-Tck", "hello");
            switch (check) {
                case "response-cookie-and-header" -> {
                    Wire.Reply reply = Wire.post(client, json, authority, Map.of(), "{ haSetCookie }");
                    expect(true, path(reply.body(), "data", "haSetCookie"), reply.body());
                    expect(List.of("set"), reply.headers().get("x-resolver"), reply.headers());
                    List<String> cookies = reply.headers().get("set-cookie");
                    if (cookies == null || cookies.stream().noneMatch(cookie -> cookie.startsWith("session=abc"))) {
                        throw new AssertionError("no cookie session=abc in " + reply.headers());
                    }
                }
                case "request-header-over-http" -> {
                    Wire.Reply reply = Wire.post(client, json, authority, tck, "{ haRequestHeader }");
                    expect("hello", path(reply.body(), "data", "haRequestHeader"), reply.body());
                }
                case "request-header-over-websocket" -> {
                    Map<String, Object> payload = Wire.websocket(client, json, authority, tck, "{ haRequestHeader }");
                    expect("hello", path(payload, "data", "haRequestHeader"), payload);
                }
                case "optional-response-over-websocket" -> {
                    Map<String, Object> payload = Wire.websocket(client, json, authority, tck, "{ haOptionalResponse }");
                    expect(false, path(payload, "data", "haOptionalResponse"), payload);
                }
                default -> {
                    Map<String, Object> payload = Wire.websocket(client, json, authority, tck, "{ haResponse haOptionalResponse }");
                    expect(1, ((List<?>) payload.get("errors")).size(), payload);
                    expect("HTTP_RESPONSE_UNAVAILABLE", path(payload, "errors", 0, "extensions", "code"), payload);
                    String message = String.valueOf(path(payload, "errors", 0, "message"));
                    if (!message.contains("Optional<MutableHttpResponse<?>>") || message.contains("NullPointerException")) {
                        throw new AssertionError("the message does not tell to declare the Optional form: " + message);
                    }
                    expect(false, path(payload, "data", "haOptionalResponse"), payload);
                }
            }
        } catch (AssertionError | RuntimeException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(check + ": interrupted", e);
        } catch (Exception e) {
            throw new AssertionError(check + ": " + e, e);
        }
    }

}
