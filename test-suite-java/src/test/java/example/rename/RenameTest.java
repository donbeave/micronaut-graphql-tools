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
package example.rename;

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

// Runs on the JVM and as a native binary (nativeTest): a field bound to a member with another name.
class RenameTest {

    private static final String PREFIXES = "micronaut.graphql.tools.resolver-accessor-prefixes";

    private static Object run(Map<String, Object> properties, String environment, String query) {
        try (ApplicationContext context = ApplicationContext.run(properties, environment)) {
            GraphQLSchema schema = context.getBean(GraphQLSchema.class);
            ExecutionResult result = GraphQL.newGraphQL(schema).build().execute(query);
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            return result.getData();
        }
    }

    private static SchemaBindingException failure(Map<String, Object> properties, String environment) {
        Throwable e = assertThrows(Exception.class, () -> run(properties, environment, "{ __typename }"));
        while (!(e instanceof SchemaBindingException) && e.getCause() != null && e.getCause() != e) {
            e = e.getCause();
        }
        assertTrue(e instanceof SchemaBindingException, e.toString());
        return (SchemaBindingException) e;
    }

    @Test
    void aFieldNamedLikeAKeywordBindsToAMemberWithAnotherName() {
        assertEquals(
                Map.of("parcel", Map.of("id", "1", "class", "parcel", "hashCode", 42, "headline", "Fragile",
                        "tagline", "Handle with care", "new", "a parcel")),
                run(Map.of(), RenameFixtures.MEMBERS,
                        "{ parcel { id class hashCode headline tagline new(prefix: \"a\") } }"));
    }

    @Test
    void aRenamedMemberAndAnotherMemberForOneFieldFailTheStartup() {
        SchemaBindingException e = failure(Map.of(), RenameFixtures.CLASH);
        assertTrue(e.getMessage().startsWith("[multiple-methods-found]"), e.getMessage());
        assertTrue(e.getMessage().contains("GraphQL coordinate: Slip.title"), e.getMessage());
    }

    @Test
    void aCoordinateBindsAResolverMethodWithAnotherName() {
        assertEquals(Map.of("default", "plain", "parcel", Map.of("id", "1", "class", "parcel", "hashCode", 42,
                        "headline", "Fragile", "tagline", "Handle with care", "new", "a parcel")),
                run(Map.of(), RenameFixtures.COORDINATES, "{ default parcel { id class hashCode headline tagline new(prefix: \"a\") } }"));
    }

    @Test
    void theAccessorPrefixesBindGetXAndIsXResolverMethodsWhenTheyAreOn() {
        assertEquals(Map.of("stamp", "stamped", "parcel", Map.of("id", "1", "author", "Jane", "active", true)),
                run(Map.of(PREFIXES, true), RenameFixtures.PREFIXES, "{ stamp parcel { id author active } }"));
        SchemaBindingException off = failure(Map.of(), RenameFixtures.PREFIXES);
        assertTrue(off.getMessage().contains("[field-not-bound]"), off.getMessage());
    }

}
