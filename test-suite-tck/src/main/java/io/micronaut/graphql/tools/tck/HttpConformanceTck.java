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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.micronaut.graphql.tools.tck.Wire.expect;
import static io.micronaut.graphql.tools.tck.Wire.path;

/**
 * The end-to-end checks of the HTTP conformance of the endpoint (the scenario {@code http-conformance}): the status and the media
 * type of every kind of response, the refusal of a mutation over {@code GET}, and the same with the upload module on and off. A
 * real client talks to the running application; each check starts the application of its own. The data-driven POST cases of the
 * scenario run in {@link Tck}.
 */
public final class HttpConformanceTck {

    private static final String SCENARIO = "http-conformance";
    private static final String GRAPHQL_RESPONSE_JSON = "application/graphql-response+json";
    private static final String JSON = "application/json";

    private static final String QUERY = "{ confEcho(value: \"hello\") }";
    private static final String MUTATION = "mutation { confMutate(value: \"changed\") }";
    private static final String COUNT = "{ confMutationCount }";
    private static final String PARSE_ERROR = "{ confEcho(value: ";
    private static final String VALIDATION_ERROR = "{ confNoSuchField }";
    private static final String VARIABLE_ERROR = "query($v: String!) { confEcho(value: $v) }";

    private static final List<String> CHECKS = List.of(
            "status-and-media-type-matrix",
            "status-and-media-type-matrix-without-upload",
            "get-mutation",
            "get-mutation-without-upload",
            "upload-module-on",
            "upload-module-off");

    private HttpConformanceTck() {
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
        Map<String, Object> properties = Tck.applicationProperties(SCENARIO);
        if (check.endsWith("-without-upload") || check.equals("upload-module-off")) {
            properties.put("micronaut.graphql.tools.upload.enabled", false);
        }
        try (ApplicationContext context = ApplicationContext.builder().properties(properties).start();
             HttpClient client = HttpClient.newHttpClient()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            Endpoint endpoint = new Endpoint(client, context.getBean(JsonMapper.class), "http://" + server.getHost() + ":" + server.getPort() + "/graphql");
            switch (check) {
                case "status-and-media-type-matrix", "status-and-media-type-matrix-without-upload" -> matrix(endpoint);
                case "get-mutation", "get-mutation-without-upload" -> getMutation(endpoint);
                case "upload-module-on" -> uploadModule(endpoint, true);
                default -> uploadModule(endpoint, false);
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

    // A request that executes answers 200; one that fails before the execution answers 400 under application/graphql-response+json and
    // 200 under application/json, also when Accept is absent or */*; an Accept that lists neither type answers 406; a method that the
    // endpoint does not serve answers 405. GET and POST agree.
    private static void matrix(Endpoint endpoint) throws Exception {
        String[][] outcomes = {{"executes", QUERY, "200"}, {"a parse error", PARSE_ERROR, "400"},
            {"a validation error", VALIDATION_ERROR, "400"}, {"a variable coercion error", VARIABLE_ERROR, "400"}};
        for (String method : new String[] {"GET", "POST"}) {
            for (String[] outcome : outcomes) {
                boolean fails = !"200".equals(outcome[2]);
                for (String accept : new String[] {null, "*/*", JSON, GRAPHQL_RESPONSE_JSON}) {
                    boolean strict = GRAPHQL_RESPONSE_JSON.equals(accept);
                    Reply reply = endpoint.send(method, accept, outcome[1]);
                    String what = method + " that " + (fails ? "has " : "") + outcome[0] + " with Accept <" + accept + ">: " + reply;
                    expect(fails && strict ? 400 : 200, reply.status(), what);
                    expect(strict ? GRAPHQL_RESPONSE_JSON : JSON, reply.mediaType(), what);
                    expect("Accept", reply.vary(), what);
                    Map<String, Object> body = reply.body(endpoint);
                    expect(!fails, body.containsKey("data"), what);
                    expect(fails, body.get("errors") instanceof List<?> errors && !errors.isEmpty(), what);
                }
                Reply refused = endpoint.send(method, "application/xml", outcome[1]);
                expect(406, refused.status(), method + " with an Accept that lists neither type: " + refused);
            }
        }
        for (String method : new String[] {"PUT", "DELETE", "PATCH"}) {
            Reply reply = endpoint.send(method, null, QUERY);
            expect(405, reply.status(), method + " is not served: " + reply);
        }
        Reply plain = endpoint.post("text/plain", "{ confEcho(value: \"hello\") }");
        expect(422, plain.status(), "a POST that carries no GraphQL request: " + plain);
        Reply graphql = endpoint.post("application/graphql", QUERY);
        expect(200, graphql.status(), "a POST of an application/graphql body: " + graphql);
        expect("hello", path(graphql.body(endpoint), "data", "confEcho"), graphql);
    }

    // A mutation does not run over GET: the answer is 405 with Allow: POST under both media types, and no resolver ran. A query works
    // over GET, a mutation works over POST, and the operation name decides which operation of a document counts.
    private static void getMutation(Endpoint endpoint) throws Exception {
        for (String accept : new String[] {null, JSON, GRAPHQL_RESPONSE_JSON}) {
            String type = GRAPHQL_RESPONSE_JSON.equals(accept) ? GRAPHQL_RESPONSE_JSON : JSON;
            Reply refused = endpoint.send("GET", accept, MUTATION);
            String what = "GET of a mutation with Accept <" + accept + ">: " + refused;
            expect(405, refused.status(), what);
            expect("POST", refused.allow(), what);
            expect(type, refused.mediaType(), what);
            expect(false, refused.body(endpoint).containsKey("data"), what);
            expect(true, refused.body(endpoint).get("errors") instanceof List<?> errors && !errors.isEmpty(), what);
            Reply count = endpoint.send("GET", accept, COUNT);
            expect(200, count.status(), "GET of a query with Accept <" + accept + ">: " + count);
            expect(0, path(count.body(endpoint), "data", "confMutationCount"), "a refused GET ran no resolver: " + count);
        }
        String document = "query Read { confEcho(value: \"read\") } mutation Write { confMutate(value: \"written\") }";
        Reply read = endpoint.send("GET", null, document, "Read");
        expect(200, read.status(), "GET that selects the query of a document: " + read);
        expect("read", path(read.body(endpoint), "data", "confEcho"), read);
        Reply write = endpoint.send("GET", null, document, "Write");
        expect(405, write.status(), "GET that selects the mutation of a document: " + write);
        expect("POST", write.allow(), write);
        for (String accept : new String[] {null, JSON, GRAPHQL_RESPONSE_JSON}) {
            Reply done = endpoint.send("POST", accept, MUTATION);
            expect(200, done.status(), "POST of a mutation with Accept <" + accept + ">: " + done);
            expect("changed", path(done.body(endpoint), "data", "confMutate"), done);
        }
        expect(3, path(endpoint.send("GET", null, COUNT).body(endpoint), "data", "confMutationCount"), "three POST mutations ran and no GET did");
    }

    // With the upload module on the classpath and enabled, a multipart request that follows the multipart request specification is
    // served and a malformed one answers 400; with the module off, the endpoint does not read multipart and answers 422. Either way the
    // JSON requests behave the same.
    private static void uploadModule(Endpoint endpoint, boolean on) throws Exception {
        Reply multipart = endpoint.multipart(QUERY, true);
        Reply malformed = endpoint.multipart(QUERY, false);
        if (on) {
            expect(200, multipart.status(), "a multipart request with the upload module on: " + multipart);
            expect("hello", path(multipart.body(endpoint), "data", "confEcho"), multipart);
            expect(400, malformed.status(), "a malformed multipart request with the upload module on: " + malformed);
        } else {
            expect(422, multipart.status(), "a multipart request with the upload module off: " + multipart);
            expect(422, malformed.status(), "a malformed multipart request with the upload module off: " + malformed);
        }
        Reply json = endpoint.send("POST", null, QUERY);
        expect(200, json.status(), "a JSON request with the upload module " + (on ? "on" : "off") + ": " + json);
        expect("hello", path(json.body(endpoint), "data", "confEcho"), json);
        Reply mutation = endpoint.send("GET", null, MUTATION);
        expect(405, mutation.status(), "a mutation over GET with the upload module " + (on ? "on" : "off") + ": " + mutation);
        expect("POST", mutation.allow(), mutation);
    }

    private record Reply(int status, String mediaType, String allow, String vary, String text) {

        Map<String, Object> body(Endpoint endpoint) throws IOException {
            return endpoint.json.readValue(text, Wire.OBJECT);
        }

        @Override
        public String toString() {
            return "status " + status + ", content type <" + mediaType + ">, body " + text;
        }
    }

    private record Endpoint(HttpClient client, JsonMapper json, String url) {

        Reply send(String method, String accept, String query) throws IOException, InterruptedException {
            return send(method, accept, query, null);
        }

        Reply send(String method, String accept, String query, String operationName) throws IOException, InterruptedException {
            HttpRequest.Builder request;
            if ("GET".equals(method)) {
                request = HttpRequest.newBuilder(URI.create(url + "?query=" + encode(query)
                        + (operationName == null ? "" : "&operationName=" + encode(operationName)))).GET();
            } else {
                Map<String, Object> payload = operationName == null ? Map.of("query", query) : Map.of("query", query, "operationName", operationName);
                request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", JSON)
                        .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
            }
            if (accept != null) {
                request.header("Accept", accept);
            }
            return reply(request.build());
        }

        Reply post(String contentType, String body) throws IOException, InterruptedException {
            return reply(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        }

        Reply multipart(String query, boolean withMap) throws IOException, InterruptedException {
            String boundary = "tck-boundary";
            StringBuilder body = new StringBuilder();
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"operations\"\r\n\r\n")
                    .append(json.writeValueAsString(Map.of("query", query))).append("\r\n");
            if (withMap) {
                body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"map\"\r\n\r\n{}\r\n");
            }
            body.append("--").append(boundary).append("--\r\n");
            return reply(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
        }

        private Reply reply(HttpRequest request) throws IOException, InterruptedException {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            int parameters = contentType.indexOf(';');
            return new Reply(response.statusCode(), parameters < 0 ? contentType : contentType.substring(0, parameters),
                    response.headers().firstValue("Allow").orElse(null), response.headers().firstValue("Vary").orElse(null), response.body());
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
