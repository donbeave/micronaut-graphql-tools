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
 * The end-to-end checks of the security integration (the scenario {@code security}): the application runs with Micronaut
 * Security, and the fixtures of the suite authenticate a request from the headers {@code X-User} and {@code X-Roles}. A failed
 * expectation is an {@link AssertionError} that names it.
 */
public final class SecurityTck {

    private static final List<String> CHECKS = List.of(
            "principal-after-async-hop",
            "anonymous-call-rejected",
            "role-required",
            "root-fields-hook",
            "websocket-upgrade-authentication",
            "disabled-module-runs-no-hook",
            "replaced-roles-finder",
            "expression-rejected");

    private SecurityTck() {
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
        Map<String, Object> properties = Tck.applicationProperties("security");
        switch (check) {
            case "disabled-module-runs-no-hook" -> properties.put("micronaut.graphql.tools.security.enabled", "false");
            case "replaced-roles-finder" -> properties.put("tck.security.finder", "team");
            case "expression-rejected" -> properties.put("tck.security.expression", "true");
            default -> {
            }
        }
        if (check.equals("expression-rejected")) {
            expectStartupFailure(properties);
            return;
        }
        try (ApplicationContext context = ApplicationContext.builder().properties(properties).start();
             HttpClient client = HttpClient.newHttpClient()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            JsonMapper json = context.getBean(JsonMapper.class);
            String authority = server.getHost() + ":" + server.getPort();
            Map<String, String> alice = Map.of("X-User", "alice", "X-Roles", "ROLE_USER");
            switch (check) {
                case "principal-after-async-hop" -> {
                    Wire.Reply reply = Wire.post(client, json, authority, alice, "{ secMe { principal service } }");
                    expect("alice", path(reply.body(), "data", "secMe", "principal"), reply.body());
                    expect("alice", path(reply.body(), "data", "secMe", "service"), reply.body());
                    Wire.Reply anonymous = Wire.post(client, json, authority, Map.of(), "{ secMe { principal service } }");
                    expect(true, path(anonymous.body(), "data", "secMe", "principal") == null, anonymous.body());
                    expect(true, path(anonymous.body(), "data", "secMe", "service") == null, anonymous.body());
                }
                case "anonymous-call-rejected" -> {
                    Wire.Reply reply = Wire.post(client, json, authority, Map.of(), "{ secOpen secAuthenticated secNobody }");
                    expect("open", path(reply.body(), "data", "secOpen"), reply.body());
                    expect(true, path(reply.body(), "data", "secAuthenticated") == null, reply.body());
                    expect(2, ((List<?>) reply.body().get("errors")).size(), reply.body());
                    expect("UNAUTHENTICATED", path(reply.body(), "errors", 0, "extensions", "code"), reply.body());
                    expect("secAuthenticated", path(reply.body(), "errors", 0, "path", 0), reply.body());
                    Wire.Reply authenticated = Wire.post(client, json, authority, alice, "{ secOpen secAuthenticated }");
                    expect("authenticated", path(authenticated.body(), "data", "secAuthenticated"), authenticated.body());
                    expect(true, authenticated.body().get("errors") == null, authenticated.body());
                }
                case "role-required" -> {
                    Wire.Reply user = Wire.post(client, json, authority, alice, "{ secAdmin }");
                    expect("FORBIDDEN", path(user.body(), "errors", 0, "extensions", "code"), user.body());
                    Wire.Reply admin = Wire.post(client, json, authority, Map.of("X-User", "root", "X-Roles", "ROLE_ADMIN"), "{ secAdmin }");
                    expect("admin", path(admin.body(), "data", "secAdmin"), admin.body());
                }
                case "root-fields-hook" -> {
                    Wire.Reply reply = Wire.post(client, json, authority, Map.of(),
                            "query Q { first: secOpen ...Roots secRoots } fragment Roots on Query { secOpen again: secOpen }");
                    expect("secOpen,secRoots", path(reply.body(), "data", "secRoots"), reply.body());
                }
                case "disabled-module-runs-no-hook" -> {
                    Wire.Reply reply = Wire.post(client, json, authority, Map.of(), "{ secOpen secAdmin secRoots }");
                    expect(true, reply.body().get("data") == null, reply.body());
                    expect("secRoots", path(reply.body(), "errors", 0, "path", 0), reply.body());
                    Wire.Reply unguarded = Wire.post(client, json, authority, Map.of(), "{ secOpen secAdmin }");
                    expect("admin", path(unguarded.body(), "data", "secAdmin"), unguarded.body());
                    expect(true, unguarded.body().get("errors") == null, unguarded.body());
                }
                case "replaced-roles-finder" -> {
                    Wire.Reply team = Wire.post(client, json, authority, Map.of("X-User", "ops", "X-Roles", "TEAM_OPS"), "{ secAdmin }");
                    expect("admin", path(team.body(), "data", "secAdmin"), team.body());
                    Wire.Reply admin = Wire.post(client, json, authority, Map.of("X-User", "root", "X-Roles", "ROLE_ADMIN"), "{ secAdmin }");
                    expect("FORBIDDEN", path(admin.body(), "errors", 0, "extensions", "code"), admin.body());
                }
                default -> {
                    Map<String, Object> payload = Wire.websocket(client, json, authority, alice, "{ secAuthenticated secMe { principal service } }");
                    expect("authenticated", path(payload, "data", "secAuthenticated"), payload);
                    expect("alice", path(payload, "data", "secMe", "principal"), payload);
                    expect("alice", path(payload, "data", "secMe", "service"), payload);
                    Map<String, Object> anonymous = Wire.websocket(client, json, authority, Map.of(), "{ secOpen secAuthenticated }");
                    expect("UNAUTHENTICATED", path(anonymous, "errors", 0, "extensions", "code"), anonymous);
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

    private static void expectStartupFailure(Map<String, Object> properties) {
        try (ApplicationContext context = ApplicationContext.builder().properties(properties).start()) {
            throw new AssertionError("expression-rejected: the application started although @Secured has an expression");
        } catch (AssertionError e) {
            throw e;
        } catch (RuntimeException e) {
            StringBuilder messages = new StringBuilder();
            for (Throwable t = e; t != null; t = t.getCause()) {
                messages.append(t.getMessage()).append('\n');
            }
            expect(true, messages.toString().contains("secGated()") && messages.toString().contains("Query.secGated") && messages.toString().contains("expression value"), messages.toString());
        }
    }

}
