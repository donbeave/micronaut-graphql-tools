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
package example.client;

import example.client.LibraryClient.Name;
import example.client.LibraryClient.Person;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.exceptions.GraphQLErrorsException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeclarativeLibraryClientTest implements TestPropertyProvider {

    @Inject
    LibraryClient client;

    @Override
    public Map<String, String> getProperties() {
        int port = SocketUtils.findAvailableTcpPort();
        return Map.of("micronaut.server.port", String.valueOf(port), "micronaut.http.services.library.url", "http://127.0.0.1:" + port);
    }

    @Test
    void aPayloadMethodReturnsTheRootFieldInEveryReturnShape() {
        List<Person> expected = List.of(new Person("test", "Test Test"));

        assertEquals(expected, client.find("t"));
        assertEquals(expected, client.findFuture("t").join());
        assertEquals(List.of(expected), Flux.from(client.findPublisher("t")).collectList().block());
    }

    @Test
    void anEnvelopeMethodReturnsTheResponse() {
        GraphQLResponse<List<Person>> response = client.findResponse("b");

        assertEquals(List.of(new Person("bob", "Bob Di")), response.data());
        assertEquals(200, response.httpResponse().code());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void anOperationOfTheClientDirectoryRunsWithItsFragment() {
        assertEquals(List.of(new Name("bob")), client.findNames("b"));
    }

    @Test
    void aQueryCanBeSentWithGetAndANullVariableIsSent() {
        assertEquals(List.of(new Name("bob")), client.findGet("b", null));
        assertEquals(List.of(new Name("test")), client.findGet("t", 1));
    }

    @Test
    void aMutationReturnsItsUnionAsTheDeclaredType() {
        LibraryClient.SignIn result = client.signIn("unknown", "x");

        assertNull(result.data());
        assertEquals(Map.of("__typename", "ValidationError", "message", "User unknown not found."), result.error());
    }

    @Test
    void aFieldErrorFailsAPayloadMethodAndNotAnEnvelopeMethod() {
        GraphQLErrorsException failure = assertThrows(GraphQLErrorsException.class, () -> client.findLimited("t", -1));
        assertFalse(failure.getErrors().isEmpty());

        GraphQLResponse<List<Name>> response = client.findLimitedResponse("t", -1);
        assertFalse(response.errors().isEmpty());
        assertNull(response.data());
        assertTrue(response.hasData());
    }

    @Test
    void aNullPayloadAndAVoidMethodWork() {
        assertNull(client.currentUser());
        client.ping();
    }

    @Test
    void theFilterOfTheServiceIdRunsAndTheHeaderParametersAreSent() {
        int before = RecordingFilter.CALLS.get();

        client.tagged("trace-1");

        assertEquals(before + 1, RecordingFilter.CALLS.get());
        assertEquals("trace-1", RecordingFilter.lastTrace);
        assertEquals("type-level-style", RecordingFilter.lastDeclared);
    }
}
