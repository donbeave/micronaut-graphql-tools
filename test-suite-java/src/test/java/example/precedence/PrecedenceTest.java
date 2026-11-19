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
package example.precedence;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): the precedence rule for a field that a getter and a type
// resolver method can both bind.
class PrecedenceTest {

    private static final String PRECEDENCE = "micronaut.graphql.tools.precedence";

    private static Object run(Map<String, Object> properties, String environment, String query) {
        try (ApplicationContext context = ApplicationContext.run(properties, environment)) {
            GraphQLSchema schema = context.getBean(GraphQLSchema.class);
            ExecutionResult result = GraphQL.newGraphQL(schema).build().execute(query);
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            return result.getData();
        }
    }

    private static void assertFails(Map<String, Object> properties, String environment, String code) {
        Throwable e = assertThrows(Exception.class, () -> run(properties, environment, "{ __typename }"));
        while (!(e instanceof SchemaBindingException) && e.getCause() != null && e.getCause() != e) {
            e = e.getCause();
        }
        assertTrue(e instanceof SchemaBindingException, e.toString());
        assertTrue(e.getMessage().startsWith("[" + code + "]"), e.getMessage());
    }

    @Test
    void aResolverThatDeclaresItsCoordinateBindsTheFieldNextToAGetter() {
        assertEquals(Map.of("gizmo", Map.of("id", "global", "name", "cog")),
                run(Map.of(), PrecedenceFixtures.RELAY, "{ gizmo { id ... on Sprocket { name } } }"));
    }

    @Test
    void anAmbiguousFieldFailsTheStartupByDefaultAndStrictFalseDoesNotChangeIt() {
        assertFails(Map.of(), PrecedenceFixtures.AMBIGUOUS, "precedence-ambiguity");
        assertFails(Map.of("micronaut.graphql.tools.strict", false), PrecedenceFixtures.AMBIGUOUS,
                "precedence-ambiguity");
    }

    @Test
    void theLenientSettingsDecideTheField() {
        assertEquals(Map.of("gadget", Map.of("label", "resolved pen")),
                run(Map.of(PRECEDENCE, "resolver-first"), PrecedenceFixtures.AMBIGUOUS, "{ gadget { label } }"));
        assertEquals(Map.of("gadget", Map.of("label", "own")),
                run(Map.of(PRECEDENCE, "property-first"), PrecedenceFixtures.AMBIGUOUS, "{ gadget { label } }"));
    }

    @Test
    void theRemovedValueStrictFailsTheStartup() {
        assertFails(Map.of(PRECEDENCE, "strict"), PrecedenceFixtures.RELAY, "removed-configuration-key");
    }

}
