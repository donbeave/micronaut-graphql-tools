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
package example.markers

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts
import io.micronaut.graphql.tools.resolver.TypeResolver
import spock.lang.Specification

// The Groovy suite runs on the JVM: resolvers declared through marker interfaces.
class MarkersSpec extends Specification {

    private static Object run(ApplicationContext context, String query) {
        def result = GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build().execute(query)
        assert result.errors.isEmpty(): result.errors.toString()
        return result.getData()
    }

    void 'marker contracts bind a generic type resolver and the operation of the marker'() {
        given:
        def context = ApplicationContext.run([:], MarkerFixtures.MARKERS)

        expect:
        run(context, '{ shelf { id slots label } stock }') ==
                [shelf: [id: '1', slots: 12, label: 'Shelf 1'], stock: 'queried']
        run(context, 'mutation { stock }') == [stock: 'mutated']

        cleanup:
        context.close()
    }

    void 'an implementation without a scope annotation is not a bean'() {
        given:
        def context = ApplicationContext.run([:], MarkerFixtures.MARKERS)

        expect:
        !context.containsBean(MarkerFixtures.UnscopedShelfQuery)
        context.containsBean(MarkerFixtures.ShelfQuery)

        cleanup:
        context.close()
    }

    void 'the visitor records the markers in the annotation metadata'() {
        given:
        def context = ApplicationContext.run([:], MarkerFixtures.MARKERS)
        def query = context.getBeanDefinition(MarkerFixtures.ShelfQuery).annotationMetadata
        def labels = context.getBeanDefinition(MarkerFixtures.ShelfLabels).annotationMetadata
        def mutation = context.getBeanDefinition(MarkerFixtures.StockMutation).annotationMetadata

        expect:
        query.stringValues(ResolverContracts, 'operations').toList() == ['query']
        query.stringValues(ResolverContracts, 'contractMethods').toList() == ['query||shelf/0']
        labels.classValues(ResolverContracts, 'value').toList() == [MarkerFixtures.Shelf]
        labels.stringValues(ResolverContracts, 'declaredBy').toList() == [TypeResolver.name]
        mutation.stringValues(ResolverContracts, 'operations').toList() == ['mutation']

        cleanup:
        context.close()
    }
}
