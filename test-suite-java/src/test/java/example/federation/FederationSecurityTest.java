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
package example.federation;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): the callers of a subgraph over HTTP, with Micronaut Security and the security module.
class FederationSecurityTest {

    private static final String SERVICE = "{ _service { sdl } }";

    private static final String PRODUCT = "{ _entities(representations: [{__typename: \"Product\", id: \"p1\"}]) { __typename ... on Product { id name } } }";

    private static final String ORDER = "{ _entities(representations: [{__typename: \"Order\", id: \"o1\"}]) { __typename ... on Order { id total } } }";

    private static final Map<String, String> ANONYMOUS = Map.of();

    private static final Map<String, String> USER = Map.of("X-User", "ursula", "X-Roles", "ROLE_USER");

    private static final Map<String, String> ADMIN = Map.of("X-User", "adam", "X-Roles", "ROLE_ADMIN");

    private static final Map<String, String> BILLING = Map.of("X-User", "bill", "X-Roles", "ROLE_BILLING");

    private record Reply(int status, String text, Map<String, Object> body) {
    }

    private static ApplicationContext start(String route, Map<String, Object> more, String... environments) {
        Map<String, Object> properties = new LinkedHashMap<>(Map.of(
                "micronaut.graphql.tools.allow.inert-directives", List.of("key"),
                "micronaut.graphql.tools.allow.unreachable", List.of("Order"),
                "micronaut.server.host", "127.0.0.1",
                "micronaut.server.port", -1,
                "micronaut.security.enabled", true,
                "micronaut.security.intercept-url-map[0].pattern", "/graphql",
                "micronaut.security.intercept-url-map[0].access[0]", route));
        properties.putAll(more);
        String[] names = new String[environments.length + 1];
        names[0] = FederationFixtures.FEDERATION;
        System.arraycopy(environments, 0, names, 1, environments.length);
        ApplicationContext context = ApplicationContext.builder(names).properties(properties).start();
        context.getBean(EmbeddedServer.class).start();
        return context;
    }

    private static Reply post(ApplicationContext context, Map<String, String> headers, String query) throws IOException, InterruptedException {
        EmbeddedServer server = context.getBean(EmbeddedServer.class);
        JsonMapper json = context.getBean(JsonMapper.class);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://" + server.getHost() + ":" + server.getPort() + "/graphql"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("query", query))));
        headers.forEach(request::header);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = response.statusCode() == 200
                    ? json.readValue(response.body(), Argument.mapOf(String.class, Object.class)) : Map.of();
            return new Reply(response.statusCode(), response.body(), body);
        }
    }

    private static Object path(Object value, Object... keys) {
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

    private static void assertForbidden(Reply reply) {
        assertEquals(200, reply.status(), reply.text());
        assertNull(reply.body().get("data"), reply.text());
        assertEquals("FORBIDDEN", path(reply.body(), "errors", 0, "extensions", "code"), reply.text());
    }

    // tag::route[]
    @Test
    void aRouteRuleStopsAnonymousCallersBeforeAnyFieldRuns() throws Exception {
        try (ApplicationContext context = start("isAuthenticated()", Map.of())) {
            Reply service = post(context, ANONYMOUS, SERVICE);
            assertEquals(401, service.status());
            assertFalse(service.text().contains("type Product"), service.text());
            Reply entities = post(context, ANONYMOUS, PRODUCT);
            assertEquals(401, entities.status());
            assertFalse(entities.text().contains("Product p1"), entities.text());

            assertForbidden(post(context, USER, SERVICE));
            assertForbidden(post(context, USER, PRODUCT));
            Reply admin = post(context, ADMIN, PRODUCT);
            assertEquals("Product p1", path(admin.body(), "data", "_entities", 0, "name"), admin.text());
        }
    }
    // end::route[]

    // tag::guard[]
    @Test
    void theGuardDecidesForEachEntityType() throws Exception {
        try (ApplicationContext context = start("isAnonymous()", Map.of())) {
            assertForbidden(post(context, ANONYMOUS, PRODUCT));
            assertForbidden(post(context, USER, PRODUCT));
            assertForbidden(post(context, ADMIN, ORDER));
            assertForbidden(post(context, BILLING, PRODUCT));

            Reply product = post(context, ADMIN, PRODUCT);
            assertEquals(Map.of("_entities", List.of(Map.of("__typename", "Product", "id", "p1", "name", "Product p1"))),
                    product.body().get("data"), product.text());
            Reply order = post(context, BILLING, ORDER);
            assertEquals(Map.of("_entities", List.of(Map.of("__typename", "Order", "id", "o1", "total", 42))),
                    order.body().get("data"), order.text());

            Reply mixed = post(context, ADMIN, "{ _entities(representations: [{__typename: \"Product\", id: \"p1\"},"
                    + " {__typename: \"Order\", id: \"o1\"}]) { __typename } }");
            assertNull(mixed.body().get("data"), mixed.text());
            assertEquals("FORBIDDEN", path(mixed.body(), "errors", 0, "extensions", "code"), mixed.text());
        }
    }
    // end::guard[]

    // tag::secured-and-entities[]
    @Test
    void securedOnAResolverDoesNotReachEntitiesButTheGuardDoes() throws Exception {
        String field = "{ product(id: \"p1\") { id name } }";
        try (ApplicationContext context = start("isAnonymous()", Map.of(), FederationFixtures.UNGUARDED)) {
            Reply secured = post(context, ANONYMOUS, field);
            assertNull(path(secured.body(), "data", "product"), secured.text());
            assertEquals("UNAUTHENTICATED", path(secured.body(), "errors", 0, "extensions", "code"), secured.text());

            Reply entities = post(context, ANONYMOUS, PRODUCT);
            assertEquals("Product p1", path(entities.body(), "data", "_entities", 0, "name"), entities.text());
        }
        try (ApplicationContext context = start("isAnonymous()", Map.of())) {
            assertForbidden(post(context, ANONYMOUS, PRODUCT));
            Reply secured = post(context, ADMIN, field);
            assertEquals("Product p1", path(secured.body(), "data", "product", "name"), secured.text());
        }
    }
    // end::secured-and-entities[]

    // tag::service[]
    @Test
    void theServiceFieldReturnsTheSdlWithIntrospectionOffToACallerTheGuardLetsThrough() throws Exception {
        try (ApplicationContext context = start("isAnonymous()", Map.of("micronaut.graphql.tools.protection.introspection", false))) {
            Reply schema = post(context, ADMIN, "{ __schema { queryType { name } } }");
            assertNull(schema.body().get("data"), schema.text());
            assertFalse(((List<?>) schema.body().get("errors")).isEmpty(), schema.text());

            Reply service = post(context, ADMIN, SERVICE);
            assertTrue(String.valueOf(path(service.body(), "data", "_service", "sdl")).contains("type Product @key"), service.text());

            assertForbidden(post(context, ANONYMOUS, SERVICE));
            assertForbidden(post(context, BILLING, SERVICE));
        }
    }
    // end::service[]

}
