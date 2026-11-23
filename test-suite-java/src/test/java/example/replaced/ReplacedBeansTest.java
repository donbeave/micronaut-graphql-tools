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
package example.replaced;

import graphql.schema.GraphQLSchema;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplacedBeansTest {

    private static final Map<String, Object> PORT = Map.of("micronaut.server.port", -1);

    @Test
    void anApplicationSchemaServesTheRequest() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, PORT, ReplacedBeansFactory.SCHEMA)) {
            ApplicationContext context = server.getApplicationContext();

            assertEquals(1, context.getBeansOfType(GraphQLSchema.class).size());
            assertNotNull(context.getBean(GraphQLSchema.class).getQueryType().getFieldDefinition("owner"));
            assertTrue(query(context, server).contains("application schema"));
        }
    }

    // the example resolvers need a DataLoader, so an application invocation comes with an application schema
    @Test
    void anApplicationInvocationServesTheRequest() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, PORT,
                ReplacedBeansFactory.SCHEMA, ReplacedBeansFactory.INVOCATION)) {
            ApplicationContext context = server.getApplicationContext();

            assertTrue(context.getBean(GraphQLInvocation.class).getClass().getName().contains("ReplacedBeansFactory"));
            assertTrue(query(context, server).contains("application invocation"));
        }
    }

    private static String query(ApplicationContext context, EmbeddedServer server) {
        try (HttpClient client = context.createBean(HttpClient.class, server.getURL())) {
            return client.toBlocking().retrieve(
                    HttpRequest.POST("/graphql", "{\"query\":\"{ owner }\"}").contentType(MediaType.APPLICATION_JSON), String.class);
        }
    }

}
