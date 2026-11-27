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
package io.micronaut.graphql.tools.benchmarks.loopback;

import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.benchmarks.BenchmarkCases;
import io.micronaut.graphql.tools.benchmarks.loopback.LoopbackModels.BookData;
import io.micronaut.graphql.tools.benchmarks.loopback.LoopbackModels.RawRequest;
import io.micronaut.graphql.tools.client.GraphQLHttpClient;
import io.micronaut.graphql.tools.client.GraphQLRequest;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.runtime.server.EmbeddedServer;

import java.util.Map;

/**
 * The loopback cases (#686): the same call of a server that answers a fixed body of 1 KB, made through the declarative client, through
 * the low-level {@link GraphQLHttpClient} and through a plain declarative HTTP client. Each call blocks the calling thread and
 * reads {@code data.book}.
 */
public final class LoopbackCases implements AutoCloseable {

    static final String ENABLED = "benchmarks.loopback";
    static final String SERVICE = "loopback";
    static final String DOCUMENT = "query Book($id: ID!) { book(id: $id) { id title pages tags } }";

    public static final String CLIENT_DECLARATIVE_LOOPBACK = "clientDeclarativeLoopback";
    public static final String CLIENT_LOW_LEVEL_LOOPBACK = "clientLowLevelLoopback";
    public static final String CLIENT_RAW_LOOPBACK = "clientRawLoopback";

    private final EmbeddedServer server;
    private final ApplicationContext context;

    public LoopbackCases() {
        int port = freePort();
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                ENABLED, true,
                "micronaut.server.port", port,
                "micronaut.http.services." + SERVICE + ".url", "http://localhost:" + port,
                "graphql.enabled", false,
                // the modules that the other cases measure are on the classpath: the loopback server needs none of them
                "micronaut.security.enabled", false,
                "micronaut.metrics.enabled", false,
                "micronaut.otel.enabled", false,
                "micronaut.graphql.tools.eager-init", false,
                "micronaut.graphql.tools.scalars.enabled", false,
                "micronaut.application.name", "loopback"));
        context = server.getApplicationContext();
    }

    private static int freePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No free port for the loopback server", e);
        }
    }

    /**
     * Whether the case names one of the loopback calls.
     *
     * @param name the name of a case
     * @return whether it is a loopback case
     */
    public static boolean isLoopback(String name) {
        return CLIENT_DECLARATIVE_LOOPBACK.equals(name) || CLIENT_LOW_LEVEL_LOOPBACK.equals(name) || CLIENT_RAW_LOOPBACK.equals(name);
    }

    public BenchmarkCases.Op op(String name) {
        return switch (name) {
            case CLIENT_DECLARATIVE_LOOPBACK -> declarative();
            case CLIENT_LOW_LEVEL_LOOPBACK -> lowLevel();
            case CLIENT_RAW_LOOPBACK -> raw();
            default -> throw new IllegalArgumentException("No loopback case " + name);
        };
    }

    private BenchmarkCases.Op declarative() {
        DeclarativeLoopClient client = context.getBean(DeclarativeLoopClient.class);
        return () -> checked(client.book("b-1"));
    }

    private BenchmarkCases.Op lowLevel() {
        GraphQLHttpClient client = context.getBean(LowLevelLoopClient.class).client();
        GraphQLRequest<BookData> request = GraphQLRequest.builder(BookData.class)
                .document(DOCUMENT)
                .operationName("Book")
                .variable("id", "b-1")
                .build();
        return () -> {
            GraphQLResponse<BookData> response = client.executeBlocking(request);
            return checked(response.data() == null ? null : response.data().book());
        };
    }

    private BenchmarkCases.Op raw() {
        RawLoopClient client = context.getBean(RawLoopClient.class);
        RawRequest request = new RawRequest(DOCUMENT, "Book", Map.of("id", "b-1"));
        return () -> checked(client.post(request).data().book());
    }

    private static Object checked(Object book) {
        if (book == null) {
            throw new IllegalStateException("The loopback call returned no book");
        }
        return book;
    }

    @Override
    public void close() {
        server.close();
    }
}
