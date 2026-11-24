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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The end-to-end checks of the {@code graphql-transport-ws} support (#29), run by each example suite against its own
 * fixtures of the scenario {@code websocket}: a real client, over a real socket, speaks the protocol to the running
 * application. Each check starts the application of its own, so that no state (a counter, a connection) leaks between
 * checks. A failed expectation is an {@link AssertionError} that names it.
 */
public final class WebSocketTck {

    /** The token that the connection interceptor of the scenario accepts, with the attribute {@code user=alice}. */
    private static final String TOKEN = "secret";
    /** The message of the exceptions that no mapper handles in the scenario: it must never reach a client. */
    private static final String SECRET = "secret internal detail";
    private static final String SUBPROTOCOL = "graphql-transport-ws";
    private static final long SECONDS = 10;

    private static final List<String> CHECKS = List.of(
            "subscribe-and-receive",
            "same-operation-id-after-reconnect",
            "operation-ids-are-per-connection",
            "rejecting-interceptor-closes",
            "accepting-interceptor-attributes",
            "transport-per-entry",
            "null-only-data-keeps-payload",
            "events-off-event-loop-with-context",
            "blocking-resolver-off-event-loop",
            "operations-of-one-connection-share-the-upgrade-request",
            "complete-cancels",
            "close-cancels",
            "abort-cancels",
            "finished-operation-frees-id",
            "finished-operation-is-not-cancelled",
            "subscribe-with-variables-and-operation-name",
            "configured-path",
            "init-timeout-configured",
            "concurrent-subscriptions-are-isolated",
            "protocol-errors",
            "publisher-error-unmapped",
            "publisher-error-mapped",
            "event-errors-customized",
            "subscription-loader-caching-off");

    private static final String CONFIGURED_PATH = "/configured-ws";

    private WebSocketTck() {
    }

    private static Map<String, Object> properties(String check) {
        Map<String, Object> properties = Tck.applicationProperties("websocket");
        if ("configured-path".equals(check)) {
            properties.put("graphql.graphql-ws.path", CONFIGURED_PATH);
        } else if ("init-timeout-configured".equals(check)) {
            properties.put("graphql.graphql-ws.connection-init-wait-timeout", "1s");
        }
        return properties;
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
        try (ApplicationContext context = ApplicationContext.builder().properties(properties(check)).start();
             HttpClient client = HttpClient.newHttpClient()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            Env env = new Env(client, context.getBean(JsonMapper.class), server.getHost() + ":" + server.getPort(),
                    "configured-path".equals(check) ? CONFIGURED_PATH : "/graphql-ws");
            switch (check) {
                case "subscribe-and-receive" -> subscribeAndReceive(env);
                case "same-operation-id-after-reconnect" -> sameOperationIdAfterReconnect(env);
                case "operation-ids-are-per-connection" -> operationIdsArePerConnection(env);
                case "rejecting-interceptor-closes" -> rejectingInterceptorCloses(env);
                case "accepting-interceptor-attributes" -> acceptingInterceptorAttributes(env);
                case "transport-per-entry" -> transportPerEntry(env);
                case "null-only-data-keeps-payload" -> nullOnlyDataKeepsPayload(env);
                case "events-off-event-loop-with-context" -> eventsOffEventLoopWithContext(env);
                case "operations-of-one-connection-share-the-upgrade-request" -> operationsShareUpgradeRequest(env);
                case "blocking-resolver-off-event-loop" -> blockingResolverOffEventLoop(env);
                case "complete-cancels" -> completeCancels(env);
                case "close-cancels" -> closeCancels(env);
                case "abort-cancels" -> abortCancels(env);
                case "finished-operation-frees-id" -> finishedOperationFreesId(env);
                case "finished-operation-is-not-cancelled" -> finishedOperationIsNotCancelled(env);
                case "subscribe-with-variables-and-operation-name" -> subscribeWithVariablesAndOperationName(env);
                case "configured-path" -> configuredPath(env);
                case "init-timeout-configured" -> initTimeoutConfigured(env);
                case "concurrent-subscriptions-are-isolated" -> concurrentSubscriptionsAreIsolated(env);
                case "subscription-loader-caching-off" -> subscriptionLoaderCachingOff(env);
                case "publisher-error-unmapped" -> publisherErrorUnmapped(env);
                case "publisher-error-mapped" -> publisherErrorMapped(env);
                case "event-errors-customized" -> eventErrorsCustomized(env);
                default -> protocolErrors(env);
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

    // A client that sends connection_init with the token and subscribes receives the events and the completion.
    private static void subscribeAndReceive(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "subscription { ticks(count: 3) { number } }");
            for (int i = 0; i < 3; i++) {
                Map<String, Object> next = socket.next();
                expect("next", next.get("type"), next);
                expect("1", next.get("id"), next);
                expect(i, path(next, "payload", "data", "ticks", "number"), next);
            }
            Map<String, Object> complete = socket.next();
            expect("complete", complete.get("type"), complete);
            expect("1", complete.get("id"), complete);
        }
    }

    // The upstream handler kept the operations of all sessions in one map and never removed them on close, so a client
    // that reconnected and used the same id was closed with 4409.
    private static void sameOperationIdAfterReconnect(Env env) throws Exception {
        try (Socket first = env.connect(Map.of())) {
            first.init(TOKEN);
            first.subscribe("1", "subscription { endless { number } }");
            expect("next", first.next().get("type"), "the first event");
        }
        try (Socket second = env.connect(Map.of())) {
            second.init(TOKEN);
            second.subscribe("1", "subscription { ticks(count: 2) { number } }");
            expect("next", second.next().get("type"), "first event of the second connection");
            expect("next", second.next().get("type"), "second event of the second connection");
            expect("complete", second.next().get("type"), "completion of the second connection");
        }
    }

    // One socket cannot complete the operation of another: the id is resolved in the session's own operations only.
    private static void operationIdsArePerConnection(Env env) throws Exception {
        try (Socket owner = env.connect(Map.of()); Socket other = env.connect(Map.of())) {
            owner.init(TOKEN);
            owner.subscribe("1", "subscription { endless { number } }");
            expect("next", owner.next().get("type"), "the first event of the owner");

            other.init(TOKEN);
            other.send(Map.of("id", "1", "type", "complete"));
            other.subscribe("1", "subscription { ticks(count: 1) { number } }");
            expect("next", other.next().get("type"), "the event of the other connection");
            expect("complete", other.next().get("type"), "the completion of the other connection");

            // the owner still receives its events: nothing the other socket did touched its operation
            for (int i = 0; i < 3; i++) {
                expect("next", owner.next().get("type"), "an event of the owner after the other socket completed id 1");
            }
            expect(0, cancelled(env), "streams cancelled while the owner still listens");
        }
    }

    private static void rejectingInterceptorCloses(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.sendInit("wrong");
            expect(4403, socket.awaitClose(), "close code of a rejected connection");
        }
    }

    private static void acceptingInterceptorAttributes(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "{ connectionUser }");
            Map<String, Object> next = socket.next();
            expect("alice", path(next, "payload", "data", "connectionUser"), next);
            expect("complete", socket.next().get("type"), "completion");
        }
    }

    // A data object that holds only a null field is a payload of its own: the next message carries it with the field set to null.
    private static void nullOnlyDataKeepsPayload(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "{ nothing }");
            Map<String, Object> next = socket.next();
            expect("next", next.get("type"), next);
            Object payload = next.get("payload");
            if (!(payload instanceof Map<?, ?> map) || !(map.get("data") instanceof Map<?, ?> data) || !data.containsKey("nothing") || data.get("nothing") != null) {
                throw new AssertionError("the payload of the next message must hold data {nothing=null}: " + next);
            }
            expect("complete", socket.next().get("type"), "completion");
        }
    }

    // A resolver reads the transport of the execution state: WEBSOCKET for an operation over graphql-transport-ws, HTTP for a POST.
    private static void transportPerEntry(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "{ transport }");
            Map<String, Object> next = socket.next();
            expect("WEBSOCKET", path(next, "payload", "data", "transport"), next);
            expect("complete", socket.next().get("type"), "completion");
        }
        HttpResponse<String> response = env.client.send(HttpRequest.newBuilder(URI.create("http://" + env.authority + "/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ transport }\"}")).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> body = env.json.readValue(response.body(), OBJECT);
        expect("HTTP", path(body, "data", "transport"), body);
    }

    // The events are resolved off the event loop although their source emits on a thread that declares itself one, and
    // the child fields see the upgrade request in the propagated context.
    private static void eventsOffEventLoopWithContext(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of("X-Tck", "hello"))) {
            socket.init(TOKEN);
            socket.subscribe("1", "subscription { ticks(count: 3) { number sourceNonBlocking onEventLoop requestHeader } }");
            for (int i = 0; i < 3; i++) {
                Map<String, Object> next = socket.next();
                expect(i, path(next, "payload", "data", "ticks", "number"), next);
                expect(true, path(next, "payload", "data", "ticks", "sourceNonBlocking"), "the source of the events emits on a non-blocking thread: " + next);
                expect(false, path(next, "payload", "data", "ticks", "onEventLoop"), next);
                expect("hello", path(next, "payload", "data", "ticks", "requestHeader"), next);
            }
            expect("complete", socket.next().get("type"), "completion");
        }
    }

    // Every operation of a connection, a query or a subscription whose events are resolved on the executor, sees the upgrade
    // request of its connection in the propagated context, the one after the other on one connection.
    private static void operationsShareUpgradeRequest(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of("X-Tck", "hello"))) {
            socket.init(TOKEN);
            socket.subscribe("1", "{ requestHeader }");
            Map<String, Object> first = socket.next();
            expect("hello", path(first, "payload", "data", "requestHeader"), first);
            expect("complete", socket.next().get("type"), "completion of the query");
            socket.subscribe("2", "subscription { ticks(count: 2) { requestHeader } }");
            for (int i = 0; i < 2; i++) {
                Map<String, Object> next = socket.next();
                expect("hello", path(next, "payload", "data", "ticks", "requestHeader"), next);
            }
            expect("complete", socket.next().get("type"), "completion of the subscription");
            socket.subscribe("3", "{ requestHeader }");
            Map<String, Object> last = socket.next();
            expect("hello", path(last, "payload", "data", "requestHeader"), last);
            expect("complete", socket.next().get("type"), "completion of the second query");
        }
    }

    // A query over the socket: its resolver sleeps, which would stall the event loop if it ran there.
    private static void blockingResolverOffEventLoop(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "{ onEventLoop }");
            Map<String, Object> next = socket.next();
            expect(false, path(next, "payload", "data", "onEventLoop"), next);
            expect("complete", socket.next().get("type"), "completion");
        }
    }

    // The loader of a subscription operation has caching off: a key that two fields load reaches the batch function twice.
    private static void subscriptionLoaderCachingOff(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "subscription { ticks(count: 1) { echo echoAgain } }");
            Map<String, Object> next = socket.next();
            expect("e1", path(next, "payload", "data", "ticks", "echo"), next);
            expect("e1", path(next, "payload", "data", "ticks", "echoAgain"), next);
            expect("complete", socket.next().get("type"), "completion");
            socket.subscribe("2", "{ echoLoaderKeys }");
            Map<String, Object> keys = socket.next();
            expect(2, path(keys, "payload", "data", "echoLoaderKeys"), keys);
        }
    }

    private static void completeCancels(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "subscription { endless { number } }");
            expect("next", socket.next().get("type"), "the first event");
            expect(0, cancelled(env), "streams cancelled before complete");
            socket.send(Map.of("id", "1", "type", "complete"));
            awaitCancelled(env, 1);
        }
    }

    private static void closeCancels(Env env) throws Exception {
        Socket socket = env.connect(Map.of());
        socket.init(TOKEN);
        socket.subscribe("1", "subscription { endless { number } }");
        socket.subscribe("2", "subscription { endless { number } }");
        expect("next", socket.next().get("type"), "the first event");
        expect(0, cancelled(env), "streams cancelled before close");
        socket.close();
        awaitCancelled(env, 2);
    }

    // A connection that drops without the closing handshake ends the session the same way a close does.
    private static void abortCancels(Env env) throws Exception {
        Socket socket = env.connect(Map.of());
        socket.init(TOKEN);
        socket.subscribe("1", "subscription { endless { number } }");
        expect("next", socket.next().get("type"), "the first event");
        expect(0, cancelled(env), "streams cancelled before the connection drops");
        socket.abort();
        awaitCancelled(env, 1);
    }

    // An operation that completed by itself leaves the session's operations: its id is free again, and the same
    // connection can run another operation under it, repeatedly.
    private static void finishedOperationFreesId(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            for (int round = 0; round < 3; round++) {
                socket.subscribe("1", "subscription { ticks(count: 1) { number } }");
                expect("next", socket.next().get("type"), "the event of round " + round);
                expect("complete", socket.next().get("type"), "the completion of round " + round);
            }
        }
    }

    // A stream that completed by itself is no longer the session's: closing the connection cancels only what still runs.
    private static void finishedOperationIsNotCancelled(Env env) throws Exception {
        Socket socket = env.connect(Map.of());
        socket.init(TOKEN);
        socket.subscribe("1", "subscription { ticks(count: 2) { number } }");
        expect("next", socket.next().get("type"), "the first event");
        expect("next", socket.next().get("type"), "the second event");
        expect("complete", socket.next().get("type"), "the completion");
        socket.subscribe("2", "subscription { endless { number } }");
        expect("next", socket.next().get("type"), "the first event of the endless stream");
        socket.close();
        awaitCancelled(env, 1);
    }

    // The payload of a subscribe message carries variables and an operation name, as the HTTP body does.
    private static void subscribeWithVariablesAndOperationName(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            String document = "subscription First($n: Int!) { ticks(count: $n) { number } } subscription Second { ticks(count: 1) { number } }";
            socket.send(Map.of("id", "1", "type", "subscribe", "payload",
                    Map.of("query", document, "variables", Map.of("n", 2), "operationName", "First")));
            for (int i = 0; i < 2; i++) {
                Map<String, Object> next = socket.next();
                expect(i, path(next, "payload", "data", "ticks", "number"), next);
            }
            expect("complete", socket.next().get("type"), "completion of the first operation");
            socket.send(Map.of("id", "2", "type", "subscribe", "payload", Map.of("query", document, "operationName", "Second")));
            Map<String, Object> next = socket.next();
            expect(0, path(next, "payload", "data", "ticks", "number"), next);
            expect("complete", socket.next().get("type"), "completion of the second operation");
        }
    }

    // graphql.graphql-ws.path moves the endpoint: the configured path speaks the protocol, the default path is gone.
    private static void configuredPath(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "subscription { ticks(count: 1) { number } }");
            expect("next", socket.next().get("type"), "the event on the configured path");
            expect("complete", socket.next().get("type"), "the completion on the configured path");
        }
        try {
            new Env(env.client, env.json, env.authority, "/graphql-ws").connect(Map.of()).close();
        } catch (ExecutionException | TimeoutException expected) {
            return;
        }
        throw new AssertionError("the default path still accepts a WebSocket after graphql.graphql-ws.path moved it");
    }

    // graphql.graphql-ws.connection-init-wait-timeout closes a connection that sends no connection_init in time.
    private static void initTimeoutConfigured(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            expect(4408, socket.awaitClose(), "a connection that never sent connection_init, with a timeout of 1s");
        }
    }

    // Clients on their own connections subscribe at the same time, each with a header of its own: every event of a client
    // shows its own header and the numbers of its own stream, in order, whatever the other streams do.
    private static void concurrentSubscriptionsAreIsolated(Env env) throws Exception {
        int clients = 8;
        int events = 20;
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int client = 0; client < clients; client++) {
                String header = "client-" + client;
                futures.add(pool.submit(() -> {
                    try (Socket socket = env.connect(Map.of("X-Tck", header))) {
                        socket.init(TOKEN);
                        socket.subscribe("1", "subscription { ticks(count: " + events + ") { number requestHeader } }");
                        for (int i = 0; i < events; i++) {
                            Map<String, Object> next = socket.next();
                            expect(i, path(next, "payload", "data", "ticks", "number"), header + ": " + next);
                            expect(header, path(next, "payload", "data", "ticks", "requestHeader"), next);
                        }
                        expect("complete", socket.next().get("type"), header + ": completion");
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(SECONDS, TimeUnit.SECONDS);
            }
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AssertionError error) {
                throw error;
            }
            throw e;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void protocolErrors(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.subscribe("1", "{ onEventLoop }");
            expect(4401, socket.awaitClose(), "subscribe before connection_init");
        }
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.sendInit(TOKEN);
            expect(4429, socket.awaitClose(), "a second connection_init");
        }
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.sendRaw("not json");
            expect(4400, socket.awaitClose(), "a message that is not JSON");
        }
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "subscription { endless { number } }");
            socket.subscribe("1", "subscription { endless { number } }");
            expect(4409, socket.awaitClose(), "the same id twice on one connection");
        }
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "{ doesNotExist }");
            Map<String, Object> error = socket.next();
            expect("error", error.get("type"), "a query that does not validate: " + error);
        }
    }

    // A publisher that fails with an exception that has no mapper ends the operation with the generic error, which the
    // customizers of the final list see, and the message of the exception never reaches the client.
    private static void publisherErrorUnmapped(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "subscription { failing { number } }");
            Map<String, Object> error = socket.next();
            expect("error", error.get("type"), error);
            expect("1", error.get("id"), error);
            Map<String, Object> first = firstError(error);
            expect("Internal server error", first.get("message"), error);
            expect("INTERNAL_ERROR", path(first, "extensions", "code"), error);
            expect("customized", path(first, "extensions", "stream"), error);
            expect(false, error.toString().contains(SECRET), "the error leaks the message of the exception: " + error);
        }
    }

    // A publisher that fails with a mapped exception sends the mapped error.
    private static void publisherErrorMapped(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "subscription { failingMapped { number } }");
            Map<String, Object> error = socket.next();
            expect("error", error.get("type"), error);
            Map<String, Object> first = firstError(error);
            expect("tick failed", first.get("message"), error);
            expect("TICK_FAILED", path(first, "extensions", "code"), error);
            expect("customized", path(first, "extensions", "stream"), error);
        }
    }

    // The result of an event that has errors is a response of its own: the customizers of the final list see its errors.
    private static void eventErrorsCustomized(Env env) throws Exception {
        try (Socket socket = env.connect(Map.of())) {
            socket.init(TOKEN);
            socket.subscribe("1", "subscription { brokenEvent { number broken } }");
            Map<String, Object> next = socket.next();
            expect("next", next.get("type"), next);
            expect(1, path(next, "payload", "data", "brokenEvent", "number"), next);
            Map<String, Object> first = firstError(next);
            expect("Internal server error", first.get("message"), next);
            expect("INTERNAL_ERROR", path(first, "extensions", "code"), next);
            expect("customized", path(first, "extensions", "stream"), next);
            expect(false, next.toString().contains(SECRET), "the error leaks the message of the exception: " + next);
            expect("complete", socket.next().get("type"), "completion");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstError(Map<String, Object> message) {
        Object errors = message.get("payload") instanceof Map<?, ?> payload ? payload.get("errors") : message.get("payload");
        if (!(errors instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> first)) {
            throw new AssertionError("expected a list of errors in the payload: " + message);
        }
        return (Map<String, Object>) first;
    }

    private static int cancelled(Env env) throws IOException, InterruptedException {
        HttpResponse<String> response = env.client.send(HttpRequest.newBuilder(URI.create("http://" + env.authority + "/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ cancelledStreams }\"}")).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> body = env.json.readValue(response.body(), OBJECT);
        return ((Number) path(body, "data", "cancelledStreams")).intValue();
    }

    private static void awaitCancelled(Env env, int expected) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SECONDS);
        int actual = cancelled(env);
        while (actual != expected && System.nanoTime() < deadline) {
            Thread.sleep(50);
            actual = cancelled(env);
        }
        expect(expected, actual, "streams cancelled");
        // nothing cancels twice: the count stays
        Thread.sleep(200);
        expect(expected, cancelled(env), "streams cancelled, a moment later");
    }

    private static final Argument<Map<String, Object>> OBJECT = Argument.mapOf(String.class, Object.class);

    @SuppressWarnings("unchecked")
    private static Object path(Map<String, Object> message, String... keys) {
        Object current = message;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return current;
    }

    private static void expect(Object expected, Object actual, Object context) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">: " + context);
        }
    }

    private record Env(HttpClient client, JsonMapper json, String authority, String path) {

        Socket connect(Map<String, String> headers) throws Exception {
            Socket socket = new Socket(json);
            WebSocket.Builder builder = client.newWebSocketBuilder().subprotocols(SUBPROTOCOL);
            headers.forEach(builder::header);
            socket.webSocket = builder.buildAsync(URI.create("ws://" + authority + path), socket.listener)
                    .get(SECONDS, TimeUnit.SECONDS);
            return socket;
        }
    }

    /** A client connection: the messages it received are queued, a close is the last entry. */
    private static final class Socket implements AutoCloseable {

        private final JsonMapper json;
        private final BlockingQueue<Object> received = new LinkedBlockingQueue<>();
        private final Listener listener = new Listener();
        private volatile WebSocket webSocket;
        private volatile boolean closed;

        Socket(JsonMapper json) {
            this.json = json;
        }

        /** Sends connection_init and waits for the acknowledgement, as a client must before it subscribes. */
        void init(String token) throws Exception {
            sendInit(token);
            Object entry = received.poll(SECONDS, TimeUnit.SECONDS);
            if (!(entry instanceof String text) || !"connection_ack".equals(json.readValue(text, OBJECT).get("type"))) {
                throw new AssertionError("expected connection_ack but received " + entry);
            }
        }

        void sendInit(String token) throws Exception {
            send(Map.of("type", "connection_init", "payload", Map.of("token", token)));
        }

        void subscribe(String id, String query) throws Exception {
            send(Map.of("id", id, "type", "subscribe", "payload", Map.of("query", query)));
        }

        void send(Map<String, Object> message) throws Exception {
            sendRaw(json.writeValueAsString(message));
        }

        void sendRaw(String text) throws Exception {
            webSocket.sendText(text, true).get(SECONDS, TimeUnit.SECONDS);
        }

        /** @return the next message that is not the acknowledgement of the connection */
        Map<String, Object> next() throws Exception {
            while (true) {
                Object entry = received.poll(SECONDS, TimeUnit.SECONDS);
                if (entry == null) {
                    throw new AssertionError("no message in " + SECONDS + " seconds");
                }
                if (entry instanceof Integer code) {
                    throw new AssertionError("the connection closed with " + code + " while a message was expected");
                }
                Map<String, Object> message = json.readValue((String) entry, OBJECT);
                if (!"connection_ack".equals(message.get("type"))) {
                    return message;
                }
            }
        }

        /** @return the close code, once the server closed the connection */
        int awaitClose() throws Exception {
            while (true) {
                Object entry = received.poll(SECONDS, TimeUnit.SECONDS);
                if (entry == null) {
                    throw new AssertionError("the connection was not closed in " + SECONDS + " seconds");
                }
                if (entry instanceof Integer code) {
                    return code;
                }
            }
        }

        void abort() {
            closed = true;
            webSocket.abort();
        }

        @Override
        public void close() {
            if (webSocket != null && !closed) {
                closed = true;
                try {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(SECONDS, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    webSocket.abort();
                }
            }
        }

        private final class Listener implements WebSocket.Listener {

            private final StringBuilder partial = new StringBuilder();

            @Override
            public void onOpen(WebSocket socket) {
                socket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                partial.append(data);
                if (last) {
                    received.add(partial.toString());
                    partial.setLength(0);
                }
                socket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
                received.add(statusCode);
                return null;
            }

            @Override
            public void onError(WebSocket socket, Throwable error) {
                received.add(1006);
            }
        }
    }

}
