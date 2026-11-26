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
package example.quickstart;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickStartTest {

    // tag::query[]
    @Test
    void theFirstQueryReturnsTheBooks() {
        try (ApplicationContext context = ApplicationContext.run()) {
            GraphQL graphQL = context.getBean(GraphQL.class);

            ExecutionResult result = graphQL.execute("{ allBooks { id title } }");

            assertTrue(result.getErrors().isEmpty());
            Map<String, List<Map<String, Object>>> data = result.getData();
            assertEquals("The Fractured Sage", data.get("allBooks").getFirst().get("title"));
        }
    }
    // end::query[]

    // tag::schema-build[]
    @Test
    void theSchemaBuilds() {
        try (ApplicationContext context = ApplicationContext.run()) {
            GraphQLSchema schema = context.getBean(GraphQLSchema.class); // getting it builds the schema

            assertNotNull(schema.getQueryType().getFieldDefinition("allBooks"));
        }
    }
    // end::schema-build[]

    @Test
    void theSameQueryOverHttpReturnsTheResponseOfTheGuide() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1))) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(server.getURL() + "/graphql"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ allBooks { id title } }\"}"))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("{\"data\":{\"allBooks\":[{\"id\":\"ab\",\"title\":\"The Fractured Sage\"},{\"id\":\"ax\",\"title\":\"Considered for Duty\"}]}}", response.body());
        }
    }

}
