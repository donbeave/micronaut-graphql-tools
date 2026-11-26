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
package io.micronaut.graphql.tools.customizer

import graphql.GraphQL
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import jakarta.inject.Singleton

import java.util.concurrent.CopyOnWriteArrayList

/**
 * One bean and several beans of a customizer type behave the same way: every bean runs, and a chain runs lowest order
 * first. The API freeze review (#98) lists each customizer that the library injects as a list.
 */
class MultipleCustomizerBeansSpec extends AbstractSpec {

    static final String SCHEMA = '''
schema { query: Query }

type Query {
  hello: String
  payload: Payload
  other: Payload
}

union Payload = Item | Other

type Item { name: String }

type Other { code: Int }
'''

    static final List<String> CALLS = new CopyOnWriteArrayList<>()

    void setup() {
        CALLS.clear()
    }

    void "several mapping dictionary customizers each add their own mapping"() {
        given:
        startContext(SCHEMA)

        when:
        def result = executeQuery('{ payload { ... on Item { name } } other { ... on Other { code } } }')

        then:
        result.errors.isEmpty()
        result.data == [payload: [name: 'item'], other: [code: 1]]
        CALLS.findAll { it.startsWith('dictionary-') } == ['dictionary-item', 'dictionary-other']
    }

    void "several type definition registry customizers each change the registry, lowest order first"() {
        given:
        startContext(SCHEMA)

        when:
        def result = executeQuery('{ first second }')

        then:
        result.errors.isEmpty()
        result.data == [first: 'first', second: 'second']
        CALLS.findAll { it.startsWith('registry-') } == ['registry-first', 'registry-second', 'registry-second-saw-first']
    }

    void "several builder customizers run lowest order first"() {
        given:
        startContext(SCHEMA)

        when:
        GraphQL graphQL = getGraphQLBean()

        then:
        graphQL != null
        CALLS.findAll { it.startsWith('builder-') } == ['builder-first', 'builder-second']
    }

    @Singleton
    @Order(2)
    @Requires(property = 'spec.name', value = 'MultipleCustomizerBeansSpec')
    static class OtherDictionary implements GraphQLSchemaMappingDictionaryCustomizer {
        @Override
        void customize(SchemaMappingDictionary dictionary) {
            CALLS << 'dictionary-other'
            dictionary.registerType('Other', Other)
        }
    }

    @Singleton
    @Order(1)
    @Requires(property = 'spec.name', value = 'MultipleCustomizerBeansSpec')
    static class ItemDictionary implements GraphQLSchemaMappingDictionaryCustomizer {
        @Override
        void customize(SchemaMappingDictionary dictionary) {
            CALLS << 'dictionary-item'
            dictionary.registerType('Item', Item)
        }
    }

    @Singleton
    @Order(2)
    @Requires(property = 'spec.name', value = 'MultipleCustomizerBeansSpec')
    static class SecondRegistry implements GraphQLTypeDefinitionRegistryCustomizer {
        @Override
        void customize(TypeDefinitionRegistry registry) {
            CALLS << 'registry-second'
            if (registry.objectTypeExtensions().get('Query')?.size() == 1) {
                CALLS << 'registry-second-saw-first'
            }
            registry.merge(new SchemaParser().parse('extend type Query { second: String }'))
        }
    }

    @Singleton
    @Order(1)
    @Requires(property = 'spec.name', value = 'MultipleCustomizerBeansSpec')
    static class FirstRegistry implements GraphQLTypeDefinitionRegistryCustomizer {
        @Override
        void customize(TypeDefinitionRegistry registry) {
            CALLS << 'registry-first'
            registry.merge(new SchemaParser().parse('extend type Query { first: String }'))
        }
    }

    @Singleton
    @Order(2)
    @Requires(property = 'spec.name', value = 'MultipleCustomizerBeansSpec')
    static class SecondBuilder implements GraphQLBuilderCustomizer {
        @Override
        void customize(GraphQL.Builder builder) {
            CALLS << 'builder-second'
        }
    }

    @Singleton
    @Order(1)
    @Requires(property = 'spec.name', value = 'MultipleCustomizerBeansSpec')
    static class FirstBuilder implements GraphQLBuilderCustomizer {
        @Override
        void customize(GraphQL.Builder builder) {
            CALLS << 'builder-first'
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = 'spec.name', value = 'MultipleCustomizerBeansSpec')
    static class Query {
        String hello() { 'world' }

        String first() { 'first' }

        String second() { 'second' }

        Payload payload() { new Item() }

        Payload other() { new Other() }
    }

    static interface Payload {
    }

    @GraphQLType
    static class Item implements Payload {
        String name = 'item'
    }

    @GraphQLType
    static class Other implements Payload {
        Integer code = 1
    }

}
