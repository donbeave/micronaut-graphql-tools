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
package example.scalars;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): the standard scalars of the scalars module, from the schema to the
// JSON of the response.
class ScalarsTest {

    private static ExecutionResult execute(ApplicationContext context, String query, Map<String, Object> variables) {
        GraphQLSchema schema = context.getBean(GraphQLSchema.class);
        return GraphQL.newGraphQL(schema).build()
                .execute(ExecutionInput.newExecutionInput().query(query).variables(variables).build());
    }

    private static String wire(ApplicationContext context, ExecutionResult result) throws IOException {
        return context.getBean(JsonMapper.class).writeValueAsString(result.toSpecification());
    }

    // tag::round-trip[]
    @Test
    void everyScalarRoundTripsWithItsDefaultFormat() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), ScalarsFixtures.DEFAULTS)) {
            ExecutionResult result = execute(context, "query ($t: OffsetDateTime!, $j: JSON) {"
                    + " bookedOn(date: \"2026-12-24\")"
                    + " bookedAt(time: $t)"
                    + " elapsed(spent: \"PT1H30M\")"
                    + " payload(data: $j)"
                    + " ticket(id: \"123e4567-e89b-12d3-a456-426614174000\")"
                    + " homepage(url: \"https://example.org/a?b=c\") }",
                    Map.of("t", "2026-12-24T10:15:30+01:00", "j", Map.of("kind", "x")));
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            assertEquals("{\"data\":{\"bookedOn\":\"2026-12-25\",\"bookedAt\":\"2026-12-24T11:15:30+01:00\","
                            + "\"elapsed\":\"PT2H\",\"payload\":{\"kind\":\"x\"},"
                            + "\"ticket\":\"123e4567-e89b-12d3-a456-426614174000\","
                            + "\"homepage\":\"https://example.org/a?b=c\"}}",
                    wire(context, result));
        }
    }
    // end::round-trip[]

    // tag::renamed[]
    @Test
    void aConfiguredNameAndFormatChangeTheTypeThatBinds() {
        Map<String, Object> properties = Map.of(
                "micronaut.graphql.tools.scalars.local-date.name", "Date",
                "micronaut.graphql.tools.scalars.local-date.format", "dd/MM/yyyy");
        try (ApplicationContext context = ApplicationContext.run(properties, ScalarsFixtures.RENAMED)) {
            assertTrue(context.getBean(GraphQLSchema.class).getType("Date") instanceof GraphQLScalarType);
            assertNull(context.getBean(GraphQLSchema.class).getType("LocalDate"));
            ExecutionResult result = execute(context, "{ bookedOn(date: \"24/12/2026\") }", Map.of());
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            assertEquals(Map.of("bookedOn", "25/12/2026"), result.getData());
        }
    }
    // end::renamed[]

    // tag::configured[]
    @Test
    void aScalarCanBeEnabledRenamedAndDisabledByProperties() {
        Map<String, Object> properties = Map.of(
                "micronaut.graphql.tools.scalars.offset-date-time.enabled", true,
                "micronaut.graphql.tools.scalars.local-date.name", "Date",
                "micronaut.graphql.tools.scalars.uuid.enabled", false);
        try (ApplicationContext context = ApplicationContext.run(properties, ScalarsFixtures.CONFIGURED)) {
            GraphQLSchema schema = context.getBean(GraphQLSchema.class);
            assertTrue(schema.getType("Date") instanceof GraphQLScalarType);
            assertNull(schema.getType("LocalDate"));
            assertTrue(schema.getType("OffsetDateTime") instanceof GraphQLScalarType);
            assertEquals(ScalarsFixtures.OWN_UUID, ((GraphQLScalarType) schema.getType("UUID")).getDescription());
            ExecutionResult result = execute(context, "{ bookedOn(date: \"2026-12-24\")"
                    + " bookedAt(time: \"2026-12-24T10:15:30+01:00\")"
                    + " ticket(id: \"123e4567-e89b-12d3-a456-426614174000\") }", Map.of());
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            assertEquals(Map.of("bookedOn", "2026-12-25", "bookedAt", "2026-12-24T11:15:30+01:00",
                    "ticket", "123e4567-e89b-12d3-a456-426614174000"), result.getData());
        }
    }
    // end::configured[]

    @Test
    void anUnknownScalarIdFailsTheStartup() {
        Map<String, Object> properties = Map.of("micronaut.graphql.tools.scalars.local-dat.enabled", false);
        Exception failure = assertThrows(Exception.class,
                () -> ApplicationContext.run(properties, ScalarsFixtures.DEFAULTS).close());
        assertTrue(rootMessages(failure).contains("micronaut.graphql.tools.scalars.local-dat"), failure.toString());
        assertTrue(rootMessages(failure).contains("local-date"), failure.toString());
    }

    @Test
    void aMisspelledPropertyOfAKnownScalarFailsTheStartup() {
        Map<String, Object> properties = Map.of("micronaut.graphql.tools.scalars.local-date.nmae", "Date");
        Exception failure = assertThrows(Exception.class,
                () -> ApplicationContext.run(properties, ScalarsFixtures.DEFAULTS).close());
        String messages = rootMessages(failure);
        assertTrue(messages.contains("micronaut.graphql.tools.scalars.local-date.nmae"), failure.toString());
        assertTrue(messages.contains("enabled, name, format"), failure.toString());
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }

    @Test
    void anInvalidValueIsAnErrorThatNamesTheScalar() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), ScalarsFixtures.DEFAULTS)) {
            ExecutionResult result = execute(context, "{ bookedOn(date: \"next week\") }", Map.of());
            assertFalse(result.getErrors().isEmpty());
            assertTrue(result.getErrors().get(0).getMessage().contains("LocalDate"), result.getErrors().toString());

            ExecutionResult variable = execute(context, "query ($d: Duration!) { elapsed(spent: $d) }",
                    Map.of("d", "90 minutes"));
            assertFalse(variable.getErrors().isEmpty());
            assertTrue(variable.getErrors().get(0).getMessage().contains("Duration"), variable.getErrors().toString());
        }
    }

    @Test
    void aJsonNumberThatCannotBeWrittenIsAnErrorThatNamesTheScalar() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), ScalarsFixtures.DEFAULTS)) {
            for (Object value : new Object[] {
                    Map.of("a", Double.NaN), Map.of("a", java.util.List.of(new java.math.BigDecimal("1e-10000"))),
                    java.util.List.of(new java.math.BigDecimal("1e10000"))}) {
                ExecutionResult result = execute(context, "query ($j: JSON) { payload(data: $j) }", Map.of("j", value));
                assertFalse(result.getErrors().isEmpty(), String.valueOf(value));
                assertTrue(result.getErrors().get(0).getMessage().contains("JSON"), result.getErrors().toString());
            }
            ExecutionResult literal = execute(context, "{ payload(data: {a: 1e10000}) }", Map.of());
            assertFalse(literal.getErrors().isEmpty());
            assertTrue(literal.getErrors().get(0).getMessage().contains("JSON"), literal.getErrors().toString());
        }
    }

}
