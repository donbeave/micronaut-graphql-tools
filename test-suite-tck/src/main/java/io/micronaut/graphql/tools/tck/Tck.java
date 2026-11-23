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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the execution-behaviour scenarios against the application of the suite that calls it. For each scenario the
 * runner starts the application with {@code tck.scenario=<id>} and the scenario's {@code config.properties}, sends every
 * operation of the scenario as a raw HTTP POST to {@code /graphql} and compares the complete response body with the
 * expected JSON: data, errors, their paths, locations and extensions.
 * <p>
 * A suite has fixtures for a scenario (resolvers, types, a schema bean) that carry
 * {@code @Requires(property = "tck.scenario", value = "<id>")}. A scenario without fixtures in a suite is listed in the
 * suite's {@code tck-exemptions.txt} with a reason; the list may only shrink, see {@link #checkExemptions()}.
 */
public final class Tck {

    /**
     * The property that activates the fixtures of one scenario.
     */
    public static final String SCENARIO_PROPERTY = "tck.scenario";

    static final String EXEMPTIONS = "tck-exemptions.txt";

    private static final String LOOPBACK = "127.0.0.1";

    private Tck() {
    }

    /**
     * @return the ids of the scenarios this suite has fixtures for: all scenarios except the exempt ones
     */
    public static List<String> scenarioIds() {
        Map<String, String> exempt = exemptions();
        return Scenarios.ids().stream().filter(id -> !exempt.containsKey(id)).toList();
    }

    /**
     * @return the scenarios the suite has no fixtures for, with the reason, from the suite's {@code tck-exemptions.txt}
     * (one {@code <id> <reason>} per line); empty when the suite has no such file
     */
    public static Map<String, String> exemptions() {
        Map<String, String> result = new LinkedHashMap<>();
        if (!Scenarios.exists(EXEMPTIONS)) {
            return result;
        }
        for (String line : Scenarios.lines(EXEMPTIONS)) {
            int space = line.indexOf(' ');
            String id = space < 0 ? line : line.substring(0, space);
            String reason = space < 0 ? "" : line.substring(space + 1).strip();
            if (reason.isEmpty()) {
                throw new AssertionError(EXEMPTIONS + ": the entry '" + id + "' has no reason. Write '<id> <reason>'.");
            }
            result.put(id, reason);
        }
        return result;
    }

    /**
     * The meta-check of a suite: every exempt id is a scenario that exists, and a scenario that is listed really has no
     * working fixtures, so that an entry is removed the moment its fixtures land (the list only shrinks).
     */
    public static void checkExemptions() {
        List<String> known = Scenarios.ids();
        List<String> problems = new ArrayList<>();
        exemptions().forEach((id, reason) -> {
            if (!known.contains(id)) {
                problems.add(EXEMPTIONS + " lists '" + id + "', which is not a scenario of tck/scenarios.txt. Remove the entry.");
                return;
            }
            try {
                run(id);
                problems.add(EXEMPTIONS + " lists '" + id + "' (" + reason + "), but its scenario passes in this suite. Remove the entry.");
            } catch (AssertionError | RuntimeException expected) {
                // the fixtures are missing, as listed
            }
        });
        if (!problems.isEmpty()) {
            throw new AssertionError(String.join("\n", problems));
        }
    }

    /**
     * The properties a scenario's application starts with: the scenario's {@code config.properties}, the property that
     * activates its fixtures, and a random port on the loopback address. The server binds the address the client connects
     * to: on a wildcard address another process with a listener on the same port of the loopback address (a Gradle daemon
     * has several) takes the connections, and the request fails with {@code header parser received no bytes} or
     * {@code Connection reset}.
     *
     * @param id the scenario id
     * @return the properties of the application
     */
    public static Map<String, Object> applicationProperties(String id) {
        Map<String, Object> properties = new LinkedHashMap<>(Scenarios.load(id).config());
        properties.put(SCENARIO_PROPERTY, id);
        properties.put("micronaut.server.host", LOOPBACK);
        properties.put("micronaut.server.port", -1);
        return properties;
    }

    /**
     * Runs one scenario.
     *
     * @param id the scenario id
     * @throws AssertionError listing every case whose response differs from the expected one
     */
    public static void run(String id) {
        Scenario scenario = Scenarios.load(id);
        Map<String, Object> properties = applicationProperties(id);

        try (ApplicationContext context = ApplicationContext.builder().properties(properties).start()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            JsonMapper json = context.getBean(JsonMapper.class);
            URI uri = URI.create("http://" + server.getHost() + ":" + server.getPort() + "/graphql");

            List<String> failures = new ArrayList<>();
            try (HttpClient client = HttpClient.newHttpClient()) {
                for (Scenario.Case testCase : scenario.cases()) {
                    String failure = check(client, uri, json, testCase);
                    if (failure != null) {
                        failures.add("scenario '" + id + "', case '" + testCase.name() + "':\n" + failure);
                    }
                }
            }
            if (!failures.isEmpty()) {
                throw new AssertionError(String.join("\n\n", failures));
            }
        }
    }

    private static String check(HttpClient client, URI uri, JsonMapper json, Scenario.Case testCase) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", testCase.query());
            if (testCase.variablesJson() != null) {
                body.put("variables", json.readValue(testCase.variablesJson(), Argument.OBJECT_ARGUMENT));
            }
            if (testCase.operationName() != null) {
                body.put("operationName", testCase.operationName());
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                return "  HTTP status " + response.statusCode() + ", expected 200. Body: " + response.body();
            }
            Object expected = json.readValue(testCase.expectedJson(), Argument.OBJECT_ARGUMENT);
            Object actual = json.readValue(response.body(), Argument.OBJECT_ARGUMENT);
            List<String> differences = JsonDiff.diff(expected, actual);
            if (differences.isEmpty()) {
                return null;
            }
            return "  " + String.join("\n  ", differences) + "\n  response: " + response.body();
        } catch (IOException e) {
            return "  " + e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "  interrupted";
        }
    }

}
