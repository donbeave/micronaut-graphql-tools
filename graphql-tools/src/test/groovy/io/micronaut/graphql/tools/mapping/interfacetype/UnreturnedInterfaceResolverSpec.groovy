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
package io.micronaut.graphql.tools.mapping.interfacetype

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/** A type resolver of an interface that no field returns binds the field on every implementation (#71). */
class UnreturnedInterfaceResolverSpec extends AbstractTest {

    static final String SPEC_NAME = "UnreturnedInterfaceResolverSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  pen: Pen
  cup: Cup
}

interface Node {
  label: String
}

type Pen implements Node {
  id: String
  label: String
}

type Cup implements Node {
  id: String
  label: String
}
"""

    void "a type resolver of an interface that no field returns binds the field on every implementation"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.strict': true])

        when:
        def result = executeQuery('{ pen { label } cup { label } }')

        then:
        result.errors.isEmpty()
        result.data == [pen: [label: 'label of p1'], cup: [label: 'label of c1']]
    }

    void "an implementation that binds the field itself still fails with multiple methods"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.own')

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.MULTIPLE_METHODS_FOUND
        e.cause.coordinate == 'Cup.label'
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '(\\.own)?')
    @Singleton
    @GraphQLRootResolver
    static class Query {
        Pen pen() { new Pen(id: 'p1') }

        Cup cup() { new Cup(id: 'c1') }
    }

    static interface Node {
        String getId()
    }

    @GraphQLType
    static class Pen implements Node {
        String id
    }

    @GraphQLType
    static class Cup implements Node {
        String id
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '(\\.own)?')
    @Singleton
    @GraphQLTypeResolver(Node.class)
    static class NodeResolver {
        String label(Node node) {
            'label of ' + node.id
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.own')
    @Singleton
    @GraphQLTypeResolver(Cup.class)
    static class CupResolver {
        String label(Cup cup) {
            'own label'
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '(\\.own)?')
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary.registerType("Node", Node.class)
        }
    }
}
