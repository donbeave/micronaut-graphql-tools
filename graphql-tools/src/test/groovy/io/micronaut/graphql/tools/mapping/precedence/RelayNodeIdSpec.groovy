package io.micronaut.graphql.tools.mapping.precedence

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.AmbiguousBindingException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

class RelayNodeIdSpec extends AbstractTest {

    static final String SPEC_NAME = "RelayNodeIdSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  node: Node
}

interface Node {
  id: ID!
}

type User implements Node {
  id: ID!
  name: String!
}
"""

    void "a Node.id resolver that declares its coordinate encodes the id of an entity that has getId() (#config)"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.coordinate', config)

        when:
        def result = executeQuery('{ node { id ... on User { name } } }')
        def manifest = applicationContext.getBean(BindingManifest)

        then:
        result.errors.isEmpty()
        result.data.node == [id: 'VXNlcjo3', name: 'ann']
        manifest.diagnostics.findAll { it.startsWith('!precedence-ambiguity') }.isEmpty()

        where:
        config << [[:], ['micronaut.graphql.tools.precedence': 'fail'],
                   ['micronaut.graphql.tools.precedence': 'property-first']]
    }

    void "without the coordinate the same pair is an ambiguity"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.plain')

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousBindingException
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'id'
    }

    void "without the coordinate, precedence resolver-first binds the resolver"() {
        given:
        startContext(SCHEMA, SPEC_NAME + '.plain', ['micronaut.graphql.tools.precedence': 'resolver-first'])

        when:
        def result = executeQuery('{ node { id } }')

        then:
        result.errors.isEmpty()
        result.data.node == [id: 'VXNlcjo3']
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(coordinate|plain)')
    @GraphQLRootResolver
    static class Query {
        Node node() {
            return new User(id: '7', name: 'ann')
        }
    }

    static interface Node {
    }

    @GraphQLType
    static class User implements Node {
        String id
        String name
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.coordinate')
    @GraphQLTypeResolver(Node.class)
    static class CoordinateNodeResolver {
        @GraphQLSchemaCoordinate("Node.id")
        String id(Node node) {
            return Base64.encoder.encodeToString("User:${(node as User).id}".toString().bytes)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + '.plain')
    @GraphQLTypeResolver(Node.class)
    static class PlainNodeResolver {
        String id(Node node) {
            return Base64.encoder.encodeToString("User:${(node as User).id}".toString().bytes)
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(coordinate|plain)')
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary.registerType("User", User.class)
        }
    }

}
