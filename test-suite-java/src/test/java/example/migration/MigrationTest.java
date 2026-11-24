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
package example.migration;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;
import io.micronaut.graphql.tools.manifest.BindingManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): the mappings that the migration page lists for resolvers written
// for graphql-java-tools.
class MigrationTest {

    private static ExecutionResult execute(Map<String, Object> properties, String environment, String query) {
        try (ApplicationContext context = ApplicationContext.run(properties, environment)) {
            GraphQLSchema schema = context.getBean(GraphQLSchema.class);
            return GraphQL.newGraphQL(schema).build().execute(query);
        }
    }

    private static Map<String, Object> data(Map<String, Object> properties, String environment, String query) {
        ExecutionResult result = execute(properties, environment, query);
        assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
        return result.getData();
    }

    private static String failureCode(Map<String, Object> properties, String environment) {
        Throwable e = assertThrows(Exception.class, () -> execute(properties, environment, "{ __typename }"));
        while (!(e instanceof SchemaBindingException) && e.getCause() != null && e.getCause() != e) {
            e = e.getCause();
        }
        assertTrue(e instanceof SchemaBindingException, e.toString());
        String message = e.getMessage();
        return message.substring(1, message.indexOf(']'));
    }

    @Test
    void rootResolversBindByMethodNameAndTheModelByItsGetters() {
        Map<String, Object> data = data(Map.of(), MigrationFixtures.MIGRATION,
                "{ note(id: \"1\") { id title done summary } missing: note(id: \"9\") { id } }");
        assertEquals(Map.of("id", "1", "title", "first", "done", false, "summary", "first"), data.get("note"));
        assertNull(data.get("missing"));
    }

    @Test
    void aTypeResolverMethodBindsAFieldTheModelDoesNotHave() {
        assertEquals(Map.of("addNote", Map.of("summary", "third")), data(Map.of(), MigrationFixtures.MIGRATION,
                "mutation { addNote(title: \"third\") { summary } }"));
    }

    @Test
    void argumentsBindByNameNotByPosition() {
        // the schema declares (tag, limit) and the method (limit, tag)
        assertEquals(Map.of("notes", List.of(Map.of("id", "2"))), data(Map.of(), MigrationFixtures.MIGRATION,
                "{ notes(tag: \"sec\", limit: 5) { id } }"));
        assertEquals(Map.of("notes", List.of(Map.of("id", "1"))), data(Map.of(), MigrationFixtures.MIGRATION,
                "{ notes(limit: 1) { id } }"));
    }

    @Test
    void aMethodNamedGetXBindsAFieldOnlyWithResolverAccessorPrefixes() {
        assertEquals("field-not-bound", failureCode(Map.of(), MigrationFixtures.PREFIXES));
        assertEquals(Map.of("label", Map.of("text", "note", "shout", "NOTE")),
                data(Map.of("micronaut.graphql.tools.resolver-accessor-prefixes", true), MigrationFixtures.PREFIXES,
                        "{ label { text shout } }"));
    }

    @Test
    void aFieldWithAnUnderscoreNeedsAnExplicitName() {
        assertEquals("field-not-bound", failureCode(Map.of(), MigrationFixtures.SNAKE_PLAIN));
        assertEquals(Map.of("stamp", Map.of("created_at", "today")),
                data(Map.of(), MigrationFixtures.SNAKE, "{ stamp { created_at } }"));
    }

    @Test
    void anOptionalArgumentIsRefusedAtStartup() {
        assertEquals("incorrect-class-mapping", failureCode(Map.of(), MigrationFixtures.OPTIONAL_ARGUMENT));
    }

    @Test
    void aMapIsNotTheSourceOfAnObjectType() {
        assertEquals("implementation-not-found", failureCode(Map.of(), MigrationFixtures.MAP));
    }

    // tag::recipe[]
    @Test
    void theMigrationSettingsTurnAFailureIntoAFindingInTheManifest() {
        assertEquals("unreachable-type", failureCode(Map.of(), MigrationFixtures.FINDINGS));

        Map<String, Object> settings = Map.of(
                "micronaut.graphql.tools.strict", false,
                "micronaut.graphql.tools.precedence", "resolver-first",
                "micronaut.graphql.tools.manifest.enabled", true);
        try (ApplicationContext context = ApplicationContext.run(settings, MigrationFixtures.FINDINGS)) {
            context.getBean(GraphQLSchema.class);

            List<String> lines = context.getBean(BindingManifest.class).getLines();
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("!unreachable-type ")), lines.toString());
        }
    }
    // end::recipe[]

}
