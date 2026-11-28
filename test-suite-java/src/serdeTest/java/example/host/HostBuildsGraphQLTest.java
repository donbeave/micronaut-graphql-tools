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
package example.host;

import example.AbstractTest;
import example.client.ExampleClient.Book;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(environments = HostGraphQLFactory.ENVIRONMENT)
class HostBuildsGraphQLTest extends AbstractTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    GraphQLSchema schema;

    @Inject
    GraphQL graphQL;

    @Inject
    OperationCounter operationCounter;

    @Test
    void theApplicationGraphQLIsTheOnlyOneAndRunsTheLibrarySchema() {
        assertEquals(1, applicationContext.getBeansOfType(GraphQL.class).size());
        assertEquals(1, applicationContext.getBeansOfType(GraphQLSchema.class).size());
        assertSame(schema, graphQL.getGraphQLSchema());
        assertNotNull(schema.getQueryType().getFieldDefinition("allBooks"));
    }

    @Test
    void requestsRunOnTheApplicationGraphQL() {
        int before = operationCounter.getOperations();

        GraphQLResponse<List<Book>> response = client.allBooks();

        assertTrue(response.errors().isEmpty());
        assertNotNull(response.data());
        assertEquals(5, response.data().size());
        assertEquals("The Fractured Sage", response.data().getFirst().title());
        assertEquals(2, response.data().getFirst().sellers().size());
        assertEquals(before + 1, operationCounter.getOperations());
    }

}
