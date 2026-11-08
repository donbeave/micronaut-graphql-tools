package io.micronaut.graphql.tools.customizer

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * The library's {@link GraphQLSchema} is a bean of its own, and the library's {@link GraphQL} bean steps aside when
 * the application provides one, as does the library's schema when the application provides its own schema.
 */
class GraphQLSchemaBeanSpec extends AbstractTest {

    static final String SPEC_NAME = "GraphQLSchemaBeanSpec"
    static final String SPEC_NAME_OWN_GRAPHQL = "GraphQLSchemaBeanSpec.ownGraphQL"
    static final String SPEC_NAME_OWN_GRAPHQL_FROM_LIBRARY_SCHEMA = "GraphQLSchemaBeanSpec.ownGraphQLFromLibrarySchema"
    static final String SPEC_NAME_OWN_SCHEMA = "GraphQLSchemaBeanSpec.ownSchema"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
}
"""

    void "the schema is a bean, and the library's GraphQL bean runs it"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def schema = applicationContext.getBean(GraphQLSchema)

        then:
        schema.queryType.getFieldDefinition('hello') != null
        getGraphQLBean().graphQLSchema.is(schema)
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "an application that provides its own GraphQL bean gets no second one"() {
        given:
        startContext(SCHEMA, SPEC_NAME_OWN_GRAPHQL)

        when:
        def graphQLs = applicationContext.getBeansOfType(GraphQL)

        then:
        graphQLs.size() == 1
        getGraphQLBean().is(OwnGraphQLFactory.CREATED)
        executeQuery('{ hello }').data == [hello: 'own']
    }

    void "an application that builds its own GraphQL bean from the library's schema"() {
        given:
        startContext(SCHEMA, SPEC_NAME_OWN_GRAPHQL_FROM_LIBRARY_SCHEMA)

        when:
        def graphQLs = applicationContext.getBeansOfType(GraphQL)

        then:
        graphQLs.size() == 1
        getGraphQLBean().is(OwnGraphQLFromLibrarySchemaFactory.CREATED)
        getGraphQLBean().graphQLSchema.is(applicationContext.getBean(GraphQLSchema))
        executeQuery('{ hello }').data == [hello: 'world']
    }

    void "an application that provides its own GraphQLSchema bean: the library builds none and runs that one"() {
        given: 'no TypeDefinitionRegistry and no resolvers: the library does not bind anything'
        startContext(null, SPEC_NAME_OWN_SCHEMA)

        when:
        def schemas = applicationContext.getBeansOfType(GraphQLSchema)

        then:
        schemas.size() == 1
        schemas.first().is(OwnSchemaFactory.CREATED)
        getGraphQLBean().graphQLSchema.is(OwnSchemaFactory.CREATED)
        executeQuery('{ hello }').data == [hello: 'own']
    }

    static GraphQLSchema ownSchema() {
        return new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(SCHEMA),
                RuntimeWiring.newRuntimeWiring()
                        .type('Query') { it.dataFetcher('hello', new StaticDataFetcher('own')) }
                        .build()
        )
    }

    @Requires(property = 'spec.name', pattern = 'GraphQLSchemaBeanSpec(|\\.ownGraphQL|\\.ownGraphQLFromLibrarySchema)')
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_OWN_GRAPHQL)
    @Factory
    static class OwnGraphQLFactory {
        static GraphQL CREATED

        @Bean
        @Singleton
        GraphQL graphQL() {
            CREATED = GraphQL.newGraphQL(ownSchema()).build()
            return CREATED
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_OWN_GRAPHQL_FROM_LIBRARY_SCHEMA)
    @Factory
    static class OwnGraphQLFromLibrarySchemaFactory {
        static GraphQL CREATED

        @Bean
        @Singleton
        GraphQL graphQL(GraphQLSchema schema) {
            CREATED = GraphQL.newGraphQL(schema).build()
            return CREATED
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_OWN_SCHEMA)
    @Factory
    static class OwnSchemaFactory {
        static GraphQLSchema CREATED

        @Bean
        @Singleton
        GraphQLSchema graphQLSchema() {
            CREATED = ownSchema()
            return CREATED
        }
    }

}
