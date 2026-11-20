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
package example.models;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): classes without an annotation bind through the introspections that
// @GraphQLModels generates, and the image needs no other metadata for them.
class ExternalModelTest {

    private static Object run(String environment, String query) {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), environment)) {
            GraphQLSchema schema = context.getBean(GraphQLSchema.class);
            ExecutionResult result = GraphQL.newGraphQL(schema).build().execute(query);
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            return result.getData();
        }
    }

    private static SchemaBindingException failure(String environment) {
        Throwable e = assertThrows(Exception.class, () -> run(environment, "{ __typename }"));
        while (!(e instanceof SchemaBindingException) && e.getCause() != null && e.getCause() != e) {
            e = e.getCause();
        }
        assertTrue(e instanceof SchemaBindingException, e.toString());
        return (SchemaBindingException) e;
    }

    @Test
    void listedClassesBindWithoutAnyOtherAnnotation() {
        assertEquals(
                Map.of("atlas", Map.of("title", "World", "region", Map.of("name", "Europe"), "pages", 320),
                        "atlases", List.of(Map.of("title", "World"), Map.of("title", "Roads"))),
                run(ExternalModelFixtures.CLASSES, "{ atlas { title region { name } pages } atlases { title } }"));
    }

    @Test
    void theClassesOfAListedPackageBind() {
        assertEquals(Map.of("chart", Map.of("label", "Sales", "axis", Map.of("unit", "EUR"))),
                run(ExternalModelFixtures.PACKAGE, "{ chart { label axis { unit } } }"));
    }

    @Test
    void aReturnedClassThatNothingListsFailsTheStartupWithTheClassAndTheField() {
        SchemaBindingException e = failure(ExternalModelFixtures.MISSING);
        assertTrue(e.getMessage().startsWith("[class-not-introspected]"), e.getMessage());
        assertTrue(e.getMessage().contains("example.external.unlisted.Unlisted"), e.getMessage());
        assertTrue(e.getMessage().contains("GraphQL coordinate: Query.unlisted"), e.getMessage());
        assertTrue(e.getMessage().contains("@GraphQLModels"), e.getMessage());
    }

    @Test
    void aClassReachedThroughAPropertyThatNothingListsFailsTheStartupWithTheField() {
        SchemaBindingException e = failure(ExternalModelFixtures.NESTED);
        assertTrue(e.getMessage().startsWith("[class-not-introspected]"), e.getMessage());
        assertTrue(e.getMessage().contains("example.external.unlisted.Inner"), e.getMessage());
        assertTrue(e.getMessage().contains("Holder.inner"), e.getMessage());
    }

}
