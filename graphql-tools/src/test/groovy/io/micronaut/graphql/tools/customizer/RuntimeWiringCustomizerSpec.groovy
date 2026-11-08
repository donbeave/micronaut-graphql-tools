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
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.RuntimeWiringCustomizer
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.RuntimeWiringOverrideException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * {@link RuntimeWiringCustomizer} beans add to the runtime wiring the library generated, lowest order first.
 * They may not replace what the library wired: a replaced data fetcher, type resolver, scalar or wiring factory would
 * silently undo the binding, so the startup fails and names every replacement.
 */
class RuntimeWiringCustomizerSpec extends AbstractTest {

    static final String SPEC_NAME = "RuntimeWiringCustomizerSpec"
    static final String SPEC_NAME_OVERRIDE = "RuntimeWiringCustomizerSpec.override"
    static final String SPEC_NAME_WIRING_FACTORY = "RuntimeWiringCustomizerSpec.wiringFactory"
    static final String SPEC_NAME_CODE_REGISTRY = "RuntimeWiringCustomizerSpec.codeRegistry"
    static final String SPEC_NAME_CODE_REGISTRY_UNWIRED = "RuntimeWiringCustomizerSpec.codeRegistryUnwired"

    // newer graphql-java lines make RuntimeWiring.Builder reject a replaced data fetcher, default data fetcher, type
    // resolver or scalar itself, at the first one; 21.3 accepts them silently
    static final boolean BUILDER_REJECTS_REPLACEMENTS = isPresent('graphql.schema.idl.errors.StrictModeWiringException')

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

    static boolean isPresent(String className) {
        try {
            Class.forName(className)
            return true
        } catch (ClassNotFoundException ignored) {
            return false
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
            // graphql-java 25.1: RuntimeWiring.Builder itself rejects the first replaced data fetcher
            assert e.cause.class.name == 'graphql.schema.idl.errors.StrictModeWiringException'
            assert e.cause.message == 'The field hello on type Query has already been defined'
        } else {
            assert e.cause instanceof RuntimeWiringOverrideException
            assert e.cause.replacements == [
                    "data fetcher of Query.hello (${ReplaceFetchers.name})".toString(),
                    "default data fetcher of Item (${ReplaceFetchers.name})".toString(),
                    "wiring factory (${ReplaceFetchers.name})".toString(),
                    "scalar Long (${ReplaceScalarAndTypeResolver.name})".toString(),
                    "type resolver of Payload (${ReplaceScalarAndTypeResolver.name})".toString(),
            ]
            assert e.cause.message == """RuntimeWiringCustomizer beans may only add to the runtime wiring, but they replaced what the library wired:
  data fetcher of Query.hello (${ReplaceFetchers.name})
  default data fetcher of Item (${ReplaceFetchers.name})
  wiring factory (${ReplaceFetchers.name})
  scalar Long (${ReplaceScalarAndTypeResolver.name})
  type resolver of Payload (${ReplaceScalarAndTypeResolver.name})
  Bind these fields with the library, wrap data fetchers with a SchemaDirectiveWiring, or change the built schema with a GraphQLSchemaCustomizer."""
        }
    }

    void "replacing a bound data fetcher or type resolver through the code registry fails on every graphql-java line"() {
        given:
        startContext(SCHEMA, SPEC_NAME_CODE_REGISTRY)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof RuntimeWiringOverrideException
        e.cause.replacements == [
                "data fetcher of Item.name, through the code registry (${ReplaceThroughCodeRegistry.name})".toString(),
                "data fetcher of Query.hello, through the code registry (${ReplaceThroughCodeRegistry.name})".toString(),
                "type resolver of Payload, through the code registry (${ReplaceThroughCodeRegistry.name})".toString(),
        ]
    }

    void "a code registry data fetcher for a field the library does not wire is allowed"() {
        given: 'Orphan is an allowed unreachable type: the library wires none of its fields'
        startContext(SCHEMA + '\ntype Orphan {\n  id: ID\n}\n', SPEC_NAME_CODE_REGISTRY_UNWIRED,
                ['micronaut.graphql.tools.allow-unreachable': ['Orphan']])

        when:
        def schema = getGraphQLBean().graphQLSchema

        then:
        schema.codeRegistry.hasDataFetcher(FieldCoordinates.coordinates('Orphan', 'id'))
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "replacing the wiring factory fails the startup on every graphql-java line"() {
        given:
        startContext(SCHEMA, SPEC_NAME_WIRING_FACTORY)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof RuntimeWiringOverrideException
        e.cause.replacements == ["wiring factory (${ReplaceWiringFactory.name})".toString()]
    }

    // declared first, runs second: @Order on a factory method
    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class RecordCallFactory {
        @Bean
        @Singleton
        @Order(20)
        RuntimeWiringCustomizer recordCall() {
            return { RuntimeWiring.Builder builder -> CALLS << 'RecordCall' } as RuntimeWiringCustomizer
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Order(10)
    static class BlockSecret implements RuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            CALLS << 'BlockSecret'
            builder.fieldVisibility(BlockedFields.newBlock().addPattern('Query.secret').build())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_OVERRIDE)
    @Singleton
    @Order(1)
    static class ReplaceFetchers implements RuntimeWiringCustomizer {
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
    static class ReplaceScalarAndTypeResolver implements RuntimeWiringCustomizer {
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
    static class ReplaceThroughCodeRegistry implements RuntimeWiringCustomizer {
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
    static class FetcherForUnwiredField implements RuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.codeRegistry(GraphQLCodeRegistry.newCodeRegistry()
                    .dataFetcher(FieldCoordinates.coordinates('Orphan', 'id'), new StaticDataFetcher('orphan')))
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_WIRING_FACTORY)
    @Singleton
    static class ReplaceWiringFactory implements RuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.wiringFactory(new WiringFactory() {})
        }
    }

    @Requires(property = 'spec.name', pattern = 'RuntimeWiringCustomizerSpec.*')
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

    @Requires(property = 'spec.name', pattern = 'RuntimeWiringCustomizerSpec.*')
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType('Item', Item.class)
                    .registerType('Other', Other.class)
        }
    }

}
