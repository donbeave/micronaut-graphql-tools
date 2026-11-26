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

import graphql.Scalars
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.StaticDataFetcher
import graphql.schema.GraphQLScalarType
import graphql.schema.TypeResolver
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.WiringFactory
import graphql.schema.visibility.BlockedFields
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.customizer.GraphQLRuntimeWiringCustomizer
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class GraphQLRuntimeWiringCustomizerSpec extends AbstractSpec {

    static final String SPEC_NAME = "GraphQLRuntimeWiringCustomizerSpec"
    static final String SPEC_NAME_OVERRIDE = "GraphQLRuntimeWiringCustomizerSpec.override"
    static final String SPEC_NAME_WIRING_FACTORY = "GraphQLRuntimeWiringCustomizerSpec.wiringFactory"
    static final String SPEC_NAME_CODE_REGISTRY = "GraphQLRuntimeWiringCustomizerSpec.codeRegistry"
    static final String SPEC_NAME_CODE_REGISTRY_UNWIRED = "GraphQLRuntimeWiringCustomizerSpec.codeRegistryUnwired"
    static final String SPEC_NAME_SHADOW = "GraphQLRuntimeWiringCustomizerSpec.shadow"
    static final String SPEC_NAME_SHADOW_JAVA = "GraphQLRuntimeWiringCustomizerSpec.shadowJava"
    static final String SPEC_NAME_SHADOW_UNBOUND_TWICE = "GraphQLRuntimeWiringCustomizerSpec.shadowUnboundTwice"
    static final String SPEC_NAME_SHADOW_ALLOWED = "GraphQLRuntimeWiringCustomizerSpec.shadowAllowed"

    // Some graphql-java lines make RuntimeWiring.Builder reject a replaced data fetcher itself, at the first one; others
    // accept it silently. Whether a class exists says nothing about that (StrictModeWiringException exists on 22.4,
    // whose builder still accepts replacements), so the spec asks the builder.
    static final boolean BUILDER_REJECTS_REPLACEMENTS = builderRejectsReplacedDataFetcher()

    static final List<String> CALLS = Collections.synchronizedList([])

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
  secret: String
  item: Item
  amount: Long
  payload: Payload
}

type Item {
  name: String
}

union Payload = Item | Other

type Other {
  code: Int
}

scalar Long
"""

    static boolean builderRejectsReplacedDataFetcher() {
        DataFetcher first = new StaticDataFetcher('first')
        DataFetcher second = new StaticDataFetcher('second')
        try {
            RuntimeWiring.newRuntimeWiring()
                    .type('Query', { it.dataFetcher('hello', first) })
                    .type('Query', { it.dataFetcher('hello', second) })
            return false
        } catch (RuntimeException ignored) {
            return true
        }
    }

    void setup() {
        CALLS.clear()
    }

    void "customizers add to the runtime wiring, lowest order first"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ hello item { name } amount payload { ... on Item { name } } }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'world', item: [name: 'item'], amount: 42L, payload: [name: 'item']]
        CALLS == ['BlockSecret', 'RecordCall']

        when: 'the field blocked by the first customizer'
        result = executeQuery('{ secret }')

        then:
        result.errors.size() == 1
        result.errors[0].message.contains('FieldUndefined')
        result.errors[0].message.contains("'secret'")
    }

    void "replacing what the library wired fails the startup and names every replacement"() {
        given:
        startContext(SCHEMA, SPEC_NAME_OVERRIDE)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        if (BUILDER_REJECTS_REPLACEMENTS) {
            assert e.cause.getCode() == BindingCode.RUNTIME_WIRING_OVERRIDE
            assert e.cause.replacements == ["data fetcher of Query.hello (${ReplaceFetchers.name})".toString()]
        } else {
            assert e.cause.getCode() == BindingCode.RUNTIME_WIRING_OVERRIDE
            assert e.cause.replacements == [
                    "data fetcher of Query.hello (${ReplaceFetchers.name})".toString(),
                    "default data fetcher of Item (${ReplaceFetchers.name})".toString(),
                    "wiring factory (${ReplaceFetchers.name})".toString(),
                    "scalar Long (${ReplaceScalarAndTypeResolver.name})".toString(),
                    "type resolver of Payload (${ReplaceScalarAndTypeResolver.name})".toString(),
            ]
            assert e.cause.message == """[runtime-wiring-override] GraphQLRuntimeWiringCustomizer beans may only add to the runtime wiring, but they replaced what the library wired:
  data fetcher of Query.hello (${ReplaceFetchers.name})
  default data fetcher of Item (${ReplaceFetchers.name})
  wiring factory (${ReplaceFetchers.name})
  scalar Long (${ReplaceScalarAndTypeResolver.name})
  type resolver of Payload (${ReplaceScalarAndTypeResolver.name})
  Bind these fields with the library, wrap data fetchers with a SchemaDirective bean, serve a field with the customizer's own data fetcher by returning its coordinate from GraphQLRuntimeWiringCustomizer#getClaimedFields() (or remove the registration to keep the library's binding), or change the built schema with a GraphQLSchemaCustomizer."""
        }
    }

    void "replacing a bound data fetcher or type resolver through the code registry fails on every graphql-java line"() {
        given:
        startContext(SCHEMA, SPEC_NAME_CODE_REGISTRY)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.RUNTIME_WIRING_OVERRIDE
        e.cause.replacements == [
                "data fetcher of Item.name, through the code registry (${ReplaceThroughCodeRegistry.name})".toString(),
                "data fetcher of Query.hello, through the code registry (${ReplaceThroughCodeRegistry.name})".toString(),
                "type resolver of Payload, through the code registry (${ReplaceThroughCodeRegistry.name})".toString(),
        ]
    }

    void "a code registry data fetcher for a field the library does not wire is allowed"() {
        given: 'Orphan is an allowed unreachable type: the library wires none of its fields'
        startContext(SCHEMA + '\ntype Orphan {\n  id: ID\n}\n', SPEC_NAME_CODE_REGISTRY_UNWIRED,
                ['micronaut.graphql.tools.allow.unreachable': ['Orphan']])

        when:
        def schema = getGraphQLBean().graphQLSchema

        then:
        schema.codeRegistry.hasDataFetcher(FieldCoordinates.coordinates('Orphan', 'id'))
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "a data fetcher for a property-bound field fails the startup on every graphql-java line, naming the coordinate, the customizer and the fix"() {
        given:
        startContext(SCHEMA, SPEC_NAME_SHADOW)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.RUNTIME_WIRING_OVERRIDE
        e.cause.replacements == [
                "data fetcher of Item.name (${ShadowBoundField.name})".toString()
        ]
        e.cause.message.contains('GraphQLRuntimeWiringCustomizer#getClaimedFields()')
        e.cause.message.contains('remove the registration')
    }

    void "a Java customizer shadowing a property-bound field fails the same way"() {
        given:
        startContext(SCHEMA, SPEC_NAME_SHADOW_JAVA)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.RUNTIME_WIRING_OVERRIDE
        e.cause.replacements == [
                "data fetcher of Item.name (${JavaShadowingCustomizer.name})".toString()
        ]
    }

    void "data fetchers for a claimed field and for a field the library does not bind are allowed"() {
        given: 'Orphan is an allowed unreachable type with no library binding; Item.label is claimed'
        startContext(SCHEMA.replace('name: String\n}', 'name: String\n  label: String\n}') + '\ntype Orphan {\n  id: ID\n}\n',
                SPEC_NAME_SHADOW_ALLOWED, ['micronaut.graphql.tools.allow.unreachable': ['Orphan']])

        expect:
        executeQuery('{ item { name label } }').data == [item: [name: 'item', label: 'claimed']]
        getGraphQLBean().graphQLSchema.codeRegistry.hasDataFetcher(FieldCoordinates.coordinates('Orphan', 'id'))
    }

    void "a customizer registering a field the library does not bind twice is not reported as a replacement of library wiring"() {
        given:
        startContext(SCHEMA + '\ntype Orphan {\n  id: ID\n}\n',
                SPEC_NAME_SHADOW_UNBOUND_TWICE, ['micronaut.graphql.tools.allow.unreachable': ['Orphan']])

        when:
        Throwable failure = null
        try {
            getGraphQLBean()
        } catch (BeanInstantiationException e) {
            failure = e.cause
        }

        then:
        BUILDER_REJECTS_REPLACEMENTS ? failure.class.name == 'graphql.schema.idl.errors.StrictModeWiringException' : failure == null
    }

    void "replacing the wiring factory fails the startup on every graphql-java line"() {
        given:
        startContext(SCHEMA, SPEC_NAME_WIRING_FACTORY)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.RUNTIME_WIRING_OVERRIDE
        e.cause.replacements == ["wiring factory (${ReplaceWiringFactory.name})".toString()]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class RecordCallFactory {
        @Bean
        @Singleton
        @Order(20)
        GraphQLRuntimeWiringCustomizer recordCall() {
            return { RuntimeWiring.Builder builder -> CALLS << 'RecordCall' } as GraphQLRuntimeWiringCustomizer
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Order(10)
    static class BlockSecret implements GraphQLRuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            CALLS << 'BlockSecret'
            builder.fieldVisibility(BlockedFields.newBlock().addPattern('Query.secret').build())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_OVERRIDE)
    @Singleton
    @Order(1)
    static class ReplaceFetchers implements GraphQLRuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.type('Query') { it.dataFetcher('hello', { 'hijacked' } as DataFetcher) }
            builder.type('Item') { it.defaultDataFetcher({ 'hijacked' } as DataFetcher) }
            builder.wiringFactory(new WiringFactory() {})
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_OVERRIDE)
    @Singleton
    @Order(2)
    static class ReplaceScalarAndTypeResolver implements GraphQLRuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.scalar(GraphQLScalarType.newScalar()
                    .name('Long')
                    .coercing(Scalars.GraphQLInt.coercing)
                    .build())
            builder.type('Payload') { it.typeResolver({ null } as TypeResolver) }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_CODE_REGISTRY)
    @Singleton
    static class ReplaceThroughCodeRegistry implements GraphQLRuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.codeRegistry(GraphQLCodeRegistry.newCodeRegistry()
                    .dataFetcher(FieldCoordinates.coordinates('Query', 'hello'), new StaticDataFetcher('replaced'))
                    .dataFetcher(FieldCoordinates.coordinates('Item', 'name'), new StaticDataFetcher('replaced'))
                    .typeResolver('Payload', { null } as TypeResolver))
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_CODE_REGISTRY_UNWIRED)
    @Singleton
    static class FetcherForUnwiredField implements GraphQLRuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.codeRegistry(GraphQLCodeRegistry.newCodeRegistry()
                    .dataFetcher(FieldCoordinates.coordinates('Orphan', 'id'), new StaticDataFetcher('orphan')))
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_SHADOW)
    @Singleton
    static class ShadowBoundField implements GraphQLRuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.type('Item') { it.dataFetcher('name', new StaticDataFetcher('HIJACKED')) }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_SHADOW_UNBOUND_TWICE)
    @Singleton
    static class RegisterUnboundTwice implements GraphQLRuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.type('Orphan') { it.dataFetcher('id', new StaticDataFetcher('one')) }
            builder.type('Orphan') { it.dataFetcher('id', new StaticDataFetcher('two')) }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_SHADOW_ALLOWED)
    @Singleton
    static class ClaimAndAddUnbound implements GraphQLRuntimeWiringCustomizer {
        @Override
        Set<FieldCoordinates> getClaimedFields() {
            return [FieldCoordinates.coordinates('Item', 'label')] as Set
        }

        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.type('Item') { it.dataFetcher('label', new StaticDataFetcher('claimed')) }
            builder.type('Orphan') { it.dataFetcher('id', new StaticDataFetcher('orphan')) }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_WIRING_FACTORY)
    @Singleton
    static class ReplaceWiringFactory implements GraphQLRuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.wiringFactory(new WiringFactory() {})
        }
    }

    @Requires(property = 'spec.name', pattern = 'GraphQLRuntimeWiringCustomizerSpec.*')
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }

        String secret() {
            return 'secret'
        }

        Item item() {
            return new Item()
        }

        Long amount() {
            return 42L
        }

        Payload payload() {
            return new Item()
        }
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

    @Requires(property = 'spec.name', pattern = 'GraphQLRuntimeWiringCustomizerSpec.*')
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        GraphQLSchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType('Item', Item.class)
                    .registerType('Other', Other.class)
        }
    }

}
