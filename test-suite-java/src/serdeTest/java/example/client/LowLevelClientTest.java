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

import io.micronaut.graphql.tools.client.GraphQLHttpClient;
import io.micronaut.graphql.tools.client.GraphQLRequest;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class LowLevelClientTest {

    @Serdeable
    record Profile(String username, String fullName) {
    }

    @Serdeable
    record Search(List<Profile> searchProfile) {
    }

    @Inject
    @GraphQLClient("/")
    GraphQLHttpClient client;

    @Test
    void aTypedQueryDecodesTheData() {
        // tag::typed-query[]
        GraphQLRequest<Search> request = GraphQLRequest.builder(Search.class)
                .document("query Find($contains: String!) { searchProfile(contains: $contains) { username fullName } }")
                .operationName("Find")
                .variable("contains", "bob")
                .build();

        GraphQLResponse<Search> response = client.executeBlocking(request);

        assertEquals(List.of(new Profile("bob", "Bob Di")), response.dataOrThrow().searchProfile());
        // end::typed-query[]
        assertEquals(200, response.httpResponse().code());
    }

    @Test
    void aDynamicQueryDecodesAMap() {
        // tag::dynamic-query[]
        GraphQLResponse<Map<String, Object>> response = client.executeBlocking(
                GraphQLRequest.builder().document("{ searchProfile(contains: \"bob\") { username } }").build());

        assertEquals(Map.of("searchProfile", List.of(Map.of("username", "bob"))), response.dataOrThrow());
        // end::dynamic-query[]
    }

}
