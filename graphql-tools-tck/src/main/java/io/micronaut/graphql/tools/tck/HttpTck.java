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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The end-to-end checks of the HTTP transport, run by each example suite against its own fixtures of the scenario
 * {@code http}: GET queries with variables and an operation name, the per-request context, and concurrent requests whose
 * contexts and DataLoader registries must never meet. A real client talks to the running application; each check starts
 * the application of its own. The data-driven POST cases of the scenario run in {@link Tck}.
 */
public final class HttpTck {

    private static final long SECONDS = 30;
    private static final int THREADS = 16;
    private static final int ROUNDS = 25;

    private static final List<String> CHECKS = List.of(
            "get-query",
            "get-query-with-variables-and-operation-name",
            "context-per-request",
            "configured-path",
            "concurrent-requests-are-isolated");

    private static final Argument<Map<String, Object>> OBJECT = Argument.mapOf(String.class, Object.class);

    private static final String CONFIGURED_PATH = "/api/graphql";

    private HttpTck() {
    }

    private static Map<String, Object> properties(String check) {
        Map<String, Object> properties = Tck.applicationProperties("http");
        if ("configured-path".equals(check)) {
            properties.put("graphql.path", CONFIGURED_PATH);
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
            Env env = new Env(client, context.getBean(JsonMapper.class), "http://" + server.getHost() + ":" + server.getPort() + ("configured-path".equals(check) ? CONFIGURED_PATH : "/graphql"));
            switch (check) {
                case "get-query" -> getQuery(env);
                case "get-query-with-variables-and-operation-name" -> getWithVariablesAndOperationName(env);
                case "context-per-request" -> contextPerRequest(env);
                case "configured-path" -> configuredPath(env, "http://" + server.getHost() + ":" + server.getPort());
                default -> concurrentRequestsAreIsolated(env);
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

    private static void getQuery(Env env) throws Exception {
        Map<String, Object> body = env.get("{ httpEcho(value: \"hello\") }", null, null);
        expect("hello", data(body).get("httpEcho"), body);
    }

    private static void getWithVariablesAndOperationName(Env env) throws Exception {
        Map<String, Object> body = env.get("query First($v: String!) { httpEcho(value: $v) } query Second { httpEcho(value: \"two\") }",
                "{\"v\":\"one\"}", "First");
        expect("one", data(body).get("httpEcho"), body);
        body = env.get("query First($v: String!) { httpEcho(value: $v) } query Second { httpEcho(value: \"two\") }",
                "{\"v\":\"one\"}", "Second");
        expect("two", data(body).get("httpEcho"), body);
    }

    // graphql.path moves the endpoint: GET and POST work on the configured path, and the default path is not served.
    private static void configuredPath(Env env, String base) throws Exception {
        Map<String, Object> get = env.get("{ httpEcho(value: \"get\") }", null, null);
        expect("get", data(get).get("httpEcho"), get);
        expect("alpha", data(env.post("{ tenant }", "alpha")).get("tenant"), "a POST on the configured path");
        HttpResponse<String> old = env.client.send(HttpRequest.newBuilder(URI.create(base + "/graphql"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ tenant }\"}")).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        expect(404, old.statusCode(), "the default path after graphql.path moved the endpoint: " + old.body());
    }

    // A header of the request reaches the resolvers of that request only; a request without the header sees nothing.
    private static void contextPerRequest(Env env) throws Exception {
        expect("alpha", data(env.post("{ tenant tenantLoaded }", "alpha")).get("tenant"), "the first request");
        Map<String, Object> second = env.post("{ tenant tenantLoaded }", "beta");
        expect("beta", data(second).get("tenant"), second);
        expect("loaded-beta", data(second).get("tenantLoaded"), second);
        expect(null, data(env.post("{ tenant }", null)).get("tenant"), "a request without the header");
    }

    // THREADS clients send ROUNDS requests each, every request with a tenant of its own, at the same time. A response that
    // shows another tenant, a loader value of another tenant or the loader of another execution fails the check.
    private static void concurrentRequestsAreIsolated(Env env) throws Exception {
        Set<Object> loaderIds = Collections.synchronizedSet(new HashSet<>());
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                int id = thread;
                futures.add(pool.submit(() -> {
                    for (int round = 0; round < ROUNDS; round++) {
                        String tenant = "t" + id + "-" + round;
                        try {
                            Map<String, Object> data = data(env.post("{ tenant tenantLoaded loaderId }", tenant));
                            if (!tenant.equals(data.get("tenant"))) {
                                failures.add(tenant + ": saw tenant " + data.get("tenant"));
                            }
                            if (!("loaded-" + tenant).equals(data.get("tenantLoaded"))) {
                                failures.add(tenant + ": loaded " + data.get("tenantLoaded"));
                            }
                            loaderIds.add(data.get("loaderId"));
                        } catch (IOException | RuntimeException | AssertionError e) {
                            failures.add(tenant + ": " + e);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " of " + THREADS * ROUNDS + " requests saw another request's data: "
                    + failures.subList(0, Math.min(5, failures.size())));
        }
        expect(THREADS * ROUNDS, loaderIds.size(), "distinct data loaders, one for each execution");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> body) {
        Object data = body.get("data");
        if (!(data instanceof Map<?, ?>)) {
            throw new AssertionError("no data in the response: " + body);
        }
        return (Map<String, Object>) data;
    }

    private static void expect(Object expected, Object actual, Object context) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">: " + context);
        }
    }

    private record Env(HttpClient client, JsonMapper json, String url) {

        Map<String, Object> get(String query, String variables, String operationName) throws IOException, InterruptedException {
            StringBuilder uri = new StringBuilder(url).append("?query=").append(encode(query));
            if (variables != null) {
                uri.append("&variables=").append(encode(variables));
            }
            if (operationName != null) {
                uri.append("&operationName=").append(encode(operationName));
            }
            return send(HttpRequest.newBuilder(URI.create(uri.toString())).header("Accept", "application/json").GET().build());
        }

        Map<String, Object> post(String query, String tenant) throws IOException, InterruptedException {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("query", query))));
            if (tenant != null) {
                request.header("X-Tenant", tenant);
            }
            return send(request.build());
        }

        private Map<String, Object> send(HttpRequest request) throws IOException, InterruptedException {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new AssertionError("HTTP status " + response.statusCode() + ", expected 200: " + response.body());
            }
            return json.readValue(response.body(), OBJECT);
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
