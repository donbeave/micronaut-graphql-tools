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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Runs on the JVM and as a native binary (nativeTest): a subgraph that serves _service and _entities, with strict mode at its default.
class FederationTest {

    private static final String PREFIX = "micronaut.graphql.tools.";

    // tag::settings[]
    private static final Map<String, Object> SETTINGS = Map.of(
            PREFIX + "allow.inert-directives", List.of("key"),
            PREFIX + "allow.unreachable", List.of("Order"));
    // end::settings[]

    private static ExecutionResult execute(ApplicationContext context, String query, Map<String, Object> variables) {
        return GraphQL.newGraphQL(context.getBean(GraphQLSchema.class)).build()
                .execute(ExecutionInput.newExecutionInput().query(query).variables(variables).build());
    }

    @Test
    void theServiceFieldReturnsTheSdlOfTheSubgraph() {
        try (ApplicationContext context = ApplicationContext.run(SETTINGS, FederationFixtures.FEDERATION)) {
            ExecutionResult result = execute(context, "{ _service { sdl } }", Map.of());
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            String sdl = (String) ((Map<?, ?>) ((Map<?, ?>) result.getData()).get("_service")).get("sdl");
            assertTrue(sdl.contains("type Product @key(fields : \"id\")"), sdl);
            assertTrue(sdl.contains("type Order @key(fields : \"id\")"), sdl);
        }
    }

    // tag::entities-query[]
    @Test
    void entitiesResolvesRepresentationsInOrder() {
        try (ApplicationContext context = ApplicationContext.run(SETTINGS, FederationFixtures.FEDERATION)) {
            ExecutionResult result = execute(context, "query ($representations: [_Any!]!) { _entities(representations: $representations) {"
                            + " __typename ... on Product { id name } ... on Order { id total } } }",
                    Map.of("representations", List.of(
                            Map.of("__typename", "Order", "id", "o1"),
                            Map.of("__typename", "Product", "id", "p1"))));
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            assertEquals(Map.of("_entities", List.of(
                    Map.of("__typename", "Order", "id", "o1", "total", 42),
                    Map.of("__typename", "Product", "id", "p1", "name", "Product p1"))), result.getData());
        }
    }
    // end::entities-query[]

    @Test
    void theRegularFieldsStillWork() {
        try (ApplicationContext context = ApplicationContext.run(SETTINGS, FederationFixtures.FEDERATION)) {
            ExecutionResult result = execute(context, "{ product(id: \"p2\") { id name } }", Map.of());
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            assertEquals(Map.of("product", Map.of("id", "p2", "name", "Product p2")), result.getData());
        }
    }

    // tag::strict[]
    @Test
    void strictModeNeedsTheTwoAllowSettings() {
        assertFindings(Map.of(PREFIX + "allow.unreachable", List.of("Order")), "unwired-directive");
        assertFindings(Map.of(PREFIX + "allow.inert-directives", List.of("key")), "unreachable-type");
    }
    // end::strict[]

    private static void assertFindings(Map<String, Object> properties, String code) {
        Throwable e = assertThrows(Exception.class,
                () -> ApplicationContext.run(properties, FederationFixtures.FEDERATION).close());
        while (!(e instanceof SchemaBindingException) && e.getCause() != null && e.getCause() != e) {
            e = e.getCause();
        }
        assertTrue(e instanceof SchemaBindingException, e.toString());
        assertTrue(e.getMessage().contains(code), e.getMessage());
    }

}
