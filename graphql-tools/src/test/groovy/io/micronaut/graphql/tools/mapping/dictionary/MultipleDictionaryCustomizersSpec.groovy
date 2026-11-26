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
package io.micronaut.graphql.tools.mapping.dictionary

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

class MultipleDictionaryCustomizersSpec extends AbstractSpec {

    static final String SPEC_NAME = "MultipleDictionaryCustomizersSpec"
    static final String CONFLICTING = "MultipleDictionaryCustomizersSpec.conflicting"

    static final List<String> CALLS = Collections.synchronizedList([])

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  find(first: Boolean!): Result
}

union Result = First | Second

type First {
  name: String!
}

type Second {
  name: String!
}
"""

    void setup() {
        CALLS.clear()
    }

    void "two customizers register their types on one dictionary, lowest order first"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ a: find(first: true) { __typename } b: find(first: false) { __typename } }')

        then:
        result.errors.isEmpty()
        result.data.a.__typename == 'First'
        result.data.b.__typename == 'Second'
        CALLS == ['FirstTypes', 'SecondTypes']
    }

    void "a type that two customizers map fails the startup"() {
        when:
        startContext(SCHEMA, CONFLICTING)
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IllegalArgumentException
        e.cause.message.contains('GraphQL type `First`')
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Order(1)
    static class FirstTypes implements GraphQLSchemaMappingDictionaryCustomizer {
        @Override
        void customize(SchemaMappingDictionary dictionary) {
            CALLS << 'FirstTypes'
            dictionary.registerType('First', First)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Order(2)
    static class SecondTypes implements GraphQLSchemaMappingDictionaryCustomizer {
        @Override
        void customize(SchemaMappingDictionary dictionary) {
            CALLS << 'SecondTypes'
            dictionary.registerType('Second', Second)
        }
    }

    @Requires(property = 'spec.name', value = CONFLICTING)
    @Singleton
    @Order(1)
    static class MapsFirst implements GraphQLSchemaMappingDictionaryCustomizer {
        @Override
        void customize(SchemaMappingDictionary dictionary) {
            dictionary.registerType('First', First).registerType('Second', Second)
        }
    }

    @Requires(property = 'spec.name', value = CONFLICTING)
    @Singleton
    @Order(2)
    static class MapsFirstAgain implements GraphQLSchemaMappingDictionaryCustomizer {
        @Override
        void customize(SchemaMappingDictionary dictionary) {
            dictionary.registerType('First', Other)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        Result find(boolean first) {
            first ? new First() : new Second()
        }
    }

    @Requires(property = 'spec.name', value = CONFLICTING)
    @Singleton
    @GraphQLRootResolver
    static class ConflictingQuery {
        Result find(boolean first) {
            first ? new First() : new Second()
        }
    }

    static interface Result {
    }

    @GraphQLType
    static class First implements Result {
        String name = 'one'
    }

    @GraphQLType
    static class Second implements Result {
        String name = 'two'
    }

    static class Other implements Result {
        String name = 'other'
    }

}
