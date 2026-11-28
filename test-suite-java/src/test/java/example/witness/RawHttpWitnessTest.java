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
package example.witness;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An independent witness of the server for the client examples of {@code serdeTest}: it posts the operations with the plain Micronaut
 * HTTP client on the Jackson databind stack, so it shares no code with the library client, and checks the wire format that the client
 * examples rely on.
 */
@MicronautTest
class RawHttpWitnessTest {

    @Inject
    @Client("/")
    HttpClient http;

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String query, Map<String, Object> variables) {
        return http.toBlocking().retrieve(HttpRequest.POST("/graphql", Map.of("query", query, "variables", variables)),
                Argument.mapOf(Argument.STRING, Argument.OBJECT_ARGUMENT));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aQueryAnswersTheBooks() {
        Map<String, Object> response = post("{ allBooks { id title } }", Map.of());

        assertNull(response.get("errors"));
        List<Map<String, Object>> books = ((Map<String, List<Map<String, Object>>>) response.get("data")).get("allBooks");
        assertEquals("The Fractured Sage", books.getFirst().get("title"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aUnionIsAnsweredWithItsTypename() {
        Map<String, Object> response = post(
                "mutation($u: String!, $p: String!) { signIn(username: $u, password: $p) { data { username } error { __typename ... on ValidationError { message } } } }",
                Map.of("u", "unknown", "p", "x"));

        Map<String, Object> signIn = ((Map<String, Map<String, Object>>) response.get("data")).get("signIn");
        assertNull(signIn.get("data"));
        assertEquals(Map.of("__typename", "ValidationError", "message", "User unknown not found."), signIn.get("error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aLongAndAnInstantAreAnsweredAsTheyAreSent() {
        Map<String, Object> response = post("query($at: Instant!, $value: Long!) { echoInstant(at: $at) echoLong(value: $value) }",
                Map.of("at", "2026-12-24T10:15:30Z", "value", 9007199254740993L));

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals("2026-12-24T10:16:30Z", data.get("echoInstant"));
        assertEquals(9007199254740993L, ((Number) data.get("echoLong")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anErrorOfANullableFieldKeepsTheOtherFields() {
        Map<String, Object> response = post("{ introduced unreliable }", Map.of());

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals("introduced introduced", data.get("introduced"));
        assertTrue(data.containsKey("unreliable"));
        assertNull(data.get("unreliable"));
        List<Map<String, Object>> errors = (List<Map<String, Object>>) response.get("errors");
        assertEquals(List.of("unreliable"), errors.getFirst().get("path"));
    }

}
