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

import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.graphql.tools.client.GraphQLHttpClient;
import io.micronaut.graphql.tools.client.GraphQLRequest;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.exceptions.GraphQLErrorsException;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryClientTest implements TestPropertyProvider {

    @Serdeable
    record Profile(String username, String fullName) {
    }

    @Serdeable
    record Search(List<Profile> searchProfile) {
    }

    @Inject
    @GraphQLClient("library")
    GraphQLHttpClient client;

    @Inject
    @Client("/")
    HttpClient http;

    @Override
    public Map<String, String> getProperties() {
        int port = SocketUtils.findAvailableTcpPort();
        return Map.of("micronaut.server.port", String.valueOf(port), "micronaut.http.services.library.url", "http://127.0.0.1:" + port);
    }

    @Test
    void aTypedQueryDecodesTheData() {
        GraphQLRequest<Search> request = GraphQLRequest.builder(Search.class)
                .document("query Find($contains: String!) { searchProfile(contains: $contains) { username fullName } }")
                .operationName("Find").variable("contains", "t").build();

        GraphQLResponse<Search> response = client.executeBlocking(request);

        assertEquals(List.of(new Profile("test", "Test Test")), response.dataOrThrow().searchProfile());
        assertEquals(200, response.httpResponse().code());
        assertEquals("application/graphql-response+json", response.httpResponse().getContentType().orElseThrow().toString());
    }

    @Test
    void aDynamicQueryDecodesAMap() {
        GraphQLResponse<Map<String, Object>> response = client.executeBlocking(
                GraphQLRequest.builder().document("{ searchProfile(contains: \"b\") { username } }").build());

        assertEquals(Map.of("searchProfile", List.of(Map.of("username", "bob"))), response.dataOrThrow());
    }

    @Test
    void aQueryCanBeSentWithGet() {
        GraphQLRequest<Search> request = GraphQLRequest.builder(Search.class)
                .document("query Find($contains: String!) { searchProfile(contains: $contains) { username fullName } }")
                .operationName("Find").variable("contains", "b").httpMethod(HttpMethod.GET).build();

        assertEquals(List.of(new Profile("bob", "Bob Di")), client.executeBlocking(request).dataOrThrow().searchProfile());
    }

    @Test
    void aMutationIsSentWithPostAndAUnionIsDecoded() {
        GraphQLResponse<Map<String, Object>> response = client.executeBlocking(GraphQLRequest.builder()
                .document("mutation($u: String!, $p: String!) { signIn(username: $u, password: $p) { data { username } error { __typename ... on ValidationError { message } } } }")
                .variable("u", "unknown").variable("p", "x").build());

        Map<?, ?> signIn = (Map<?, ?>) response.dataOrThrow().get("signIn");
        assertNull(signIn.get("data"));
        assertEquals(Map.of("__typename", "ValidationError", "message", "User unknown not found."), signIn.get("error"));
    }

    @Test
    void aMutationOverGetIsAnswered405WithAnErrorResponseThatNamesPost() {
        GraphQLRequest<Map<String, Object>> request = GraphQLRequest.builder()
                .document("mutation { signIn(username: \"test\", password: \"test\") { data { username } } }").httpMethod(HttpMethod.GET).build();

        GraphQLResponse<Map<String, Object>> response = client.executeBlocking(request);

        assertEquals(405, response.httpResponse().code());
        assertEquals("POST", response.httpResponse().getHeaders().get("Allow"));
        assertFalse(response.errors().isEmpty());
        assertThrows(GraphQLErrorsException.class, response::dataOrThrow);
    }

    @Test
    void aRequestErrorIsAResponseWithErrorsAndNoData() {
        GraphQLResponse<Map<String, Object>> response = client.executeBlocking(GraphQLRequest.builder().document("{ searchProfile(").build());

        assertEquals(400, response.httpResponse().code());
        assertFalse(response.hasData());
        assertFalse(response.errors().isEmpty());
        assertThrows(GraphQLErrorsException.class, response::dataOrThrow);
    }

    @Test
    void aFieldErrorKeepsTheErrorsAndTheNullData() {
        GraphQLResponse<Map<String, Object>> response = client.executeBlocking(
                GraphQLRequest.builder().document("{ searchProfile(contains: \"t\", limit: -1) { username } }").build());

        assertEquals(200, response.httpResponse().code());
        assertTrue(response.hasData());
        assertNull(response.data());
        assertFalse(response.errors().isEmpty());
    }

    @Test
    void theFilterOfTheServiceIdRunsOnEveryCall() {
        int before = RecordingFilter.CALLS.get();

        client.executeBlocking(GraphQLRequest.builder().document("{ currentUser { username } }").build());
        client.executeAsync(GraphQLRequest.builder().document("{ currentUser { username } }").build()).join();

        assertEquals(before + 2, RecordingFilter.CALLS.get());
    }

    @Test
    void aBlockingCallIsRefusedOnAnEventLoopAndWorksOnAWorker() {
        assertEquals("blocking-on-event-loop", http.toBlocking().retrieve("/client-probe/event-loop"));
        assertEquals("{currentUser=null}", http.toBlocking().retrieve("/client-probe/worker"));
    }
}
