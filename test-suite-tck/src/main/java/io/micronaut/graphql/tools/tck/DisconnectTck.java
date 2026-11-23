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
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.runtime.server.EmbeddedServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The end-to-end checks of the cancellation on an HTTP disconnect (#429), run by each example suite against its own
 * fixtures of the scenario {@code disconnect}: a real client sends a request whose first field is slow and closes the
 * connection before that field completes. A control request that stays connected shows that the later field runs when
 * nobody disconnects, so the disconnect check cannot pass because the field never runs at all. Each check starts the
 * application of its own, so that no counter leaks between checks.
 */
public final class DisconnectTck {

    private static final String OPERATION = "{ slowParent { child } }";
    private static final long SECONDS = 10;
    private static final long SETTLE_MILLIS = 1500;

    private static final List<String> CHECKS = List.of(
            "connected-client-runs-later-fields",
            "disconnect-stops-later-fields");

    private static final Argument<Map<String, Object>> OBJECT = Argument.mapOf(String.class, Object.class);

    private DisconnectTck() {
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
        try (ApplicationContext context = ApplicationContext.builder().properties(Tck.applicationProperties("disconnect")).start();
             HttpClient client = HttpClient.newHttpClient()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            Env env = new Env(client, context.getBean(JsonMapper.class), server.getHost(), server.getPort());
            if (check.equals("connected-client-runs-later-fields")) {
                connectedClientRunsLaterFields(env);
            } else {
                disconnectStopsLaterFields(env);
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

    private static void connectedClientRunsLaterFields(Env env) throws Exception {
        HttpResponse<String> response = env.post(OPERATION);
        expect(200, response.statusCode(), response.body());
        expect(1, env.count("childRuns"), "child fields that ran for a client that stayed connected");
    }

    // The client sends the request and closes the connection while the first field is pending. Netty cancels the
    // subscription to the response, which cancels the execution, and the first field's completion then aborts the
    // execution instead of starting the child field.
    private static void disconnectStopsLaterFields(Env env) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(env.host, env.port), (int) TimeUnit.SECONDS.toMillis(SECONDS));
            byte[] body = ("{\"query\":\"" + OPERATION + "\"}").getBytes(StandardCharsets.UTF_8);
            String head = "POST /graphql HTTP/1.1\r\nHost: " + env.host + ":" + env.port + "\r\n"
                    + "Content-Type: application/json\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
            awaitStarts(env, 1);
        }
        Thread.sleep(SETTLE_MILLIS);
        expect(0, env.count("childRuns"), "child fields that ran after the client disconnected");
    }

    private static void awaitStarts(Env env, int expected) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SECONDS);
        int actual = env.count("slowStarts");
        while (actual != expected && System.nanoTime() < deadline) {
            Thread.sleep(20);
            actual = env.count("slowStarts");
        }
        expect(expected, actual, "slow fields started before the client disconnected");
    }

    private static void expect(Object expected, Object actual, Object context) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">: " + context);
        }
    }

    private record Env(HttpClient client, JsonMapper json, String host, int port) {

        HttpResponse<String> post(String query) throws IOException, InterruptedException {
            return client.send(HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + "/graphql"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"" + query + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
        }

        int count(String field) throws IOException, InterruptedException {
            Map<String, Object> body = json.readValue(post("{ " + field + " }").body(), OBJECT);
            return ((Number) ((Map<?, ?>) body.get("data")).get(field)).intValue();
        }
    }
}
