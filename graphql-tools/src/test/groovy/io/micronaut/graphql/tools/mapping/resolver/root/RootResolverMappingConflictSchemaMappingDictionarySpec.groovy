package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.MappingConflictException
import org.intellij.lang.annotations.Language

class RootResolverMappingConflictSchemaMappingDictionarySpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverMappingConflictSchemaMappingDictionarySpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  user: User
}

type User {
  username: String!
}
"""

    void "attempt map a single type to a different implementations by using SchemaMappingDictionary"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MappingConflictException
        e.cause.message == """Unable to map GraphQL type `User` to ${User.name}, as it is already mapped to ${AnotherUser.name}.
  GraphQL object type: Query
  GraphQL field: user
  Mapped class: ${Query.name}
  Mapped method: user()"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'user'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == 'user()'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User user() {
            return new User(username: 'test')
        }
    }

    @GraphQLType
    static class User {
        String username
    }

    @GraphQLType
    static class AnotherUser {
        String username
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @io.micronaut.context.annotation.Factory
    static class GraphQLFactory {
        @Bean
        @jakarta.inject.Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("User", AnotherUser.class)
        }
    }

}
