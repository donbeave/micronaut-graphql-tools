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
package example.markers;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts;
import io.micronaut.graphql.tools.resolver.TypeResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): resolvers declared through marker interfaces bind with no
// reflective annotation read.
class MarkersTest {

    private static Object run(ApplicationContext context, String query) {
        GraphQLSchema schema = context.getBean(GraphQLSchema.class);
        var result = GraphQL.newGraphQL(schema).build().execute(query);
        assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
        return result.getData();
    }

    @Test
    void markerContractsBindAGenericTypeResolverAndTheOperationOfTheMarker() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), MarkerFixtures.MARKERS)) {
            assertEquals(Map.of("shelf", Map.of("id", "1", "slots", 12, "label", "Shelf 1"), "stock", "queried"),
                    run(context, "{ shelf { id slots label } stock }"));
            assertEquals(Map.of("stock", "mutated"), run(context, "mutation { stock }"));
        }
    }

    @Test
    void anImplementationWithoutAScopeAnnotationIsNotABean() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), MarkerFixtures.MARKERS)) {
            assertFalse(context.containsBean(MarkerFixtures.UnscopedShelfQuery.class));
            assertTrue(context.containsBean(MarkerFixtures.ShelfQuery.class));
        }
    }

    @Test
    void theVisitorRecordsTheMarkersInTheAnnotationMetadata() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), MarkerFixtures.MARKERS)) {
            AnnotationMetadata query = context.getBeanDefinition(MarkerFixtures.ShelfQuery.class).getAnnotationMetadata();
            AnnotationMetadata labels = context.getBeanDefinition(MarkerFixtures.ShelfLabels.class).getAnnotationMetadata();
            AnnotationMetadata mutation = context.getBeanDefinition(MarkerFixtures.StockMutation.class).getAnnotationMetadata();

            assertEquals(List.of("query"), List.of(query.stringValues(ResolverContracts.class, "operations")));
            assertEquals(List.of("query||shelf/0"), List.of(query.stringValues(ResolverContracts.class, "contractMethods")));
            assertEquals(List.of(MarkerFixtures.Shelf.class), List.of(labels.classValues(ResolverContracts.class, "value")));
            assertEquals(List.of(TypeResolver.class.getName()),
                    List.of(labels.stringValues(ResolverContracts.class, "declaredBy")));
            assertEquals(List.of("mutation"), List.of(mutation.stringValues(ResolverContracts.class, "operations")));
        }
    }
}
