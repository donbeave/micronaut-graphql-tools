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
package example.tck;

import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.tck.Tck;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two operations on one WebSocket connection never share a DataLoader cache: each operation gets its own registry, so the
 * batch function of the {@code data-loader} scenario runs once for each operation, not once for the connection.
 */
class WebSocketLoadersTest {

    private static final String QUERY = "{ authors { name volumes { title } } }";

    @Test
    @Timeout(60)
    void twoOperationsOnOneConnectionLoadInTwoBatches() throws Exception {
        Map<String, Object> properties = new java.util.LinkedHashMap<>(Tck.applicationProperties("data-loader"));
        properties.put("graphql.graphql-apollo-ws.enabled", true);
        HttpClient client = HttpClient.newHttpClient();
        try (ApplicationContext context = ApplicationContext.builder().properties(properties).start()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            String authority = server.getHost() + ":" + server.getPort();
            Messages messages = new Messages();
            WebSocket socket = client.newWebSocketBuilder().subprotocols("graphql-ws")
                    .buildAsync(URI.create("ws://" + authority + "/graphql-ws"), messages).get(10, TimeUnit.SECONDS);

            socket.sendText("{\"type\":\"connection_init\"}", true).get(10, TimeUnit.SECONDS);
            assertTrue(messages.next().contains("connection_ack"));

            for (String id : new String[] {"1", "2"}) {
                socket.sendText("{\"id\":\"" + id + "\",\"type\":\"start\",\"payload\":{\"query\":\"" + QUERY + "\"}}", true)
                        .get(10, TimeUnit.SECONDS);
                String next = messages.next();
                assertTrue(next.contains("\"data\"") && next.contains("grace-2"), next);
                assertTrue(messages.next().contains("complete"));
            }
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS);

            HttpResponse<String> stats = client.send(HttpRequest.newBuilder(URI.create("http://" + authority + "/graphql"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ loaderStats { batches keys } }\"}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(stats.body().contains("\"batches\":2") && stats.body().contains("\"keys\":6"), stats.body());
        }
    }

    private static final class Messages implements WebSocket.Listener {

        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                // the server sends keep-alive messages between the ones that matter
            if (!partial.toString().contains("\"ka\"")) {
                queue.add(partial.toString());
            }
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            queue.add("closed " + statusCode + " " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            queue.add("error " + error);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        String next() throws InterruptedException {
            String message = queue.poll(10, TimeUnit.SECONDS);
            assertNotNull(message, "no message in 10 seconds");
            return message;
        }
    }

}
