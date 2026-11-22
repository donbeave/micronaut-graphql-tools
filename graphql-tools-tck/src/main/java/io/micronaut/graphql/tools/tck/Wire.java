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

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One operation over a real socket, for the checks that need request headers: a POST over HTTP and a one-operation
 * {@code graphql-transport-ws} connection, both with headers of the caller's choice.
 */
final class Wire {

    static final Argument<Map<String, Object>> OBJECT = Argument.mapOf(String.class, Object.class);

    private static final long SECONDS = 10;

    private Wire() {
    }

    /**
     * @param status  the HTTP status
     * @param headers the response headers, names in lower case
     * @param body    the parsed JSON body
     */
    record Reply(int status, Map<String, List<String>> headers, Map<String, Object> body) {
    }

    static Reply post(HttpClient client, JsonMapper json, String authority, Map<String, String> headers, String query)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://" + authority + "/graphql"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("query", query))));
        headers.forEach(builder::header);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reply(response.statusCode(), response.headers().map(), json.readValue(response.body(), OBJECT));
    }

    /**
     * Opens a connection with the headers on the upgrade request, runs the operation and returns the {@code payload} of the
     * first {@code next} message.
     */
    static Map<String, Object> websocket(HttpClient client, JsonMapper json, String authority, Map<String, String> headers,
                                         String query) throws Exception {
        return websocket(client, json, authority, headers, List.of(query)).get(0);
    }

    /**
     * Opens one connection with the headers on the upgrade request, runs the operations one after the other on it, and
     * returns the {@code payload} of the first {@code next} message of each.
     */
    static List<Map<String, Object>> websocket(HttpClient client, JsonMapper json, String authority, Map<String, String> headers,
                                               List<String> queries) throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) {
                    received.add(buffer.toString());
                    buffer.setLength(0);
                }
                webSocket.request(1);
                return null;
            }
        };
        WebSocket.Builder builder = client.newWebSocketBuilder().subprotocols("graphql-transport-ws");
        headers.forEach(builder::header);
        WebSocket socket = builder.buildAsync(URI.create("ws://" + authority + "/graphql-ws"), listener).get(SECONDS, TimeUnit.SECONDS);
        try {
            socket.sendText(json.writeValueAsString(Map.of("type", "connection_init")), true).get(SECONDS, TimeUnit.SECONDS);
            Map<String, Object> ack = json.readValue(poll(received), OBJECT);
            if (!"connection_ack".equals(ack.get("type"))) {
                throw new AssertionError("expected connection_ack but received " + ack);
            }
            List<Map<String, Object>> payloads = new ArrayList<>();
            for (int i = 0; i < queries.size(); i++) {
                String id = String.valueOf(i + 1);
                socket.sendText(json.writeValueAsString(Map.of("id", id, "type", "subscribe", "payload", Map.of("query", queries.get(i)))), true)
                        .get(SECONDS, TimeUnit.SECONDS);
                Map<String, Object> next = json.readValue(poll(received), OBJECT);
                while ("complete".equals(next.get("type")) && !id.equals(next.get("id"))) {
                    next = json.readValue(poll(received), OBJECT);
                }
                if (!"next".equals(next.get("type")) || !id.equals(next.get("id"))) {
                    throw new AssertionError("expected next of operation " + id + " but received " + next);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) next.get("payload");
                if (payload == null) {
                    throw new AssertionError("the next message of operation " + id + " has no payload: " + next);
                }
                payloads.add(payload);
            }
            return payloads;
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    private static String poll(BlockingQueue<String> received) throws InterruptedException {
        String message = received.poll(SECONDS, TimeUnit.SECONDS);
        if (message == null) {
            throw new AssertionError("no message in " + SECONDS + " seconds");
        }
        return message;
    }

    static Object path(Object value, Object... keys) {
        Object current = value;
        for (Object key : keys) {
            if (key instanceof Integer index) {
                current = current instanceof List<?> list && index < list.size() ? list.get(index) : null;
            } else {
                current = current instanceof Map<?, ?> map ? map.get(key) : null;
            }
        }
        return current;
    }

    static void expect(Object expected, Object actual, Object context) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">: " + context);
        }
    }

}
