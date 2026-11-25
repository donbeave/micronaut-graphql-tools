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
package example.manifest;

import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.manifest.BindingManifest;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingManifestEndpointTest {

    private static final Map<String, Object> BASE = Map.of(
            "micronaut.server.port", -1,
            "micronaut.server.host", "127.0.0.1",
            "micronaut.metrics.binders.executor.enabled", false,
            "micronaut.security.enabled", false);

    @Test
    void theEndpointServesTheManifestAsJson() {
        try (EmbeddedServer server = run(Map.of("micronaut.graphql.tools.manifest.enabled", true, "endpoints.graphql-binding.enabled", true,
                "endpoints.graphql-binding.sensitive", false));
             HttpClient client = client(server)) {
            Map<?, ?> body = client.toBlocking().retrieve(HttpRequest.GET("/graphql-binding"), Map.class);
            BindingManifest manifest = server.getApplicationContext().getBean(BindingManifest.class);

            assertEquals(manifest.getHeader(), body.get("header"));
            assertEquals(manifest.getLines(), body.get("lines"));
            assertTrue(((List<?>) body.get("lines")).stream().anyMatch(it -> it.toString().startsWith("Query.")));
        }
    }

    @Test
    void theStandardEnabledKeySwitchesTheEndpointOff() {
        try (EmbeddedServer server = run(Map.of("micronaut.graphql.tools.manifest.enabled", true,
                "endpoints.graphql-binding.enabled", false));
             HttpClient client = client(server)) {
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                    () -> client.toBlocking().exchange(HttpRequest.GET("/graphql-binding")));

            assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        }
    }

    @Test
    void theStandardSensitiveKeyDecidesWhetherTheEndpointNeedsAuthentication() {
        Map<String, Object> secured = Map.of("micronaut.graphql.tools.manifest.enabled", true, "endpoints.graphql-binding.enabled", true,
                "micronaut.security.enabled", true, "endpoints.graphql-binding.sensitive", true);
        try (EmbeddedServer server = run(secured); HttpClient client = client(server)) {
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                    () -> client.toBlocking().exchange(HttpRequest.GET("/graphql-binding")));
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        }
        Map<String, Object> open = Map.of("micronaut.graphql.tools.manifest.enabled", true, "endpoints.graphql-binding.enabled", true,
                "micronaut.security.enabled", true, "endpoints.graphql-binding.sensitive", false);
        try (EmbeddedServer server = run(open); HttpClient client = client(server)) {
            assertEquals(HttpStatus.OK, client.toBlocking().exchange(HttpRequest.GET("/graphql-binding")).getStatus());
        }
    }

    @Test
    void theEndpointIsOffUntilItIsEnabledExplicitly() {
        try (EmbeddedServer server = run(Map.of("micronaut.graphql.tools.manifest.enabled", true,
                "endpoints.graphql-binding.sensitive", false));
             HttpClient client = client(server)) {
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                    () -> client.toBlocking().exchange(HttpRequest.GET("/graphql-binding")));

            assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        }
    }

    @Test
    void withoutTheRecordedManifestNoEndpointIsRegistered() {
        try (EmbeddedServer server = run(Map.of("micronaut.graphql.tools.manifest.enabled", false)); HttpClient client = client(server)) {
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                    () -> client.toBlocking().exchange(HttpRequest.GET("/graphql-binding")));

            assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        }
    }

    private static EmbeddedServer run(Map<String, Object> properties) {
        Map<String, Object> all = new java.util.HashMap<>(BASE);
        all.putAll(properties);
        return ApplicationContext.run(EmbeddedServer.class, all);
    }

    private static HttpClient client(EmbeddedServer server) {
        return server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

}
