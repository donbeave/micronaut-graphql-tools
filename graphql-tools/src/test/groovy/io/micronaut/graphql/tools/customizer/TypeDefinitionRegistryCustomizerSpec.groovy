package io.micronaut.graphql.tools.customizer

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * {@link TypeDefinitionRegistryCustomizer} beans change the registry the library binds, lowest order first,
 * before the binding starts; the application's own {@link TypeDefinitionRegistry} bean stays unchanged.
 */
class TypeDefinitionRegistryCustomizerSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeDefinitionRegistryCustomizerSpec"

    static final List<String> CALLS = Collections.synchronizedList([])

    // no schema definition: without the customizers the startup fails with SchemaDefinitionNotProvidedException
    @Language("GraphQL")
    static final String SCHEMA = """
type Query {
  hello: String
}
"""

    void setup() {
        CALLS.clear()
    }

    void "customizers change the registry before the library binds it, lowest order first"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ hello }')

        then:
        result.errors.isEmpty()
        result.data.hello == 'world'
        CALLS == ['AddSchemaDefinition', 'ExpectSchemaDefinition']
    }

    void "the application's TypeDefinitionRegistry bean is not changed"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        getGraphQLBean().graphQLSchema.queryType.name == 'Query'
        applicationContext.getBean(TypeDefinitionRegistry).schemaDefinition().isEmpty()
    }

    // declared first, runs second: @Order(20) against getOrder() == 10
    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Order(20)
    static class ExpectSchemaDefinition implements TypeDefinitionRegistryCustomizer {
        @Override
        void customize(TypeDefinitionRegistry registry) {
            CALLS << 'ExpectSchemaDefinition'
            assert registry.schemaDefinition().isPresent()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class AddSchemaDefinition implements TypeDefinitionRegistryCustomizer, Ordered {
        @Override
        void customize(TypeDefinitionRegistry registry) {
            CALLS << 'AddSchemaDefinition'
            registry.merge(new SchemaParser().parse('schema { query: Query }'))
        }

        @Override
        int getOrder() {
            return 10
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }
    }

}
