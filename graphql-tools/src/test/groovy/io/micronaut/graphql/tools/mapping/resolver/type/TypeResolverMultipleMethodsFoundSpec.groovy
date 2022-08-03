package io.micronaut.graphql.tools.mapping.resolver.type

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException
import org.intellij.lang.annotations.Language

class TypeResolverMultipleMethodsFoundSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverMultipleMethodsFoundSpec"

    @Language("GraphQL")
    static String SCHEMA = """
schema {
  query: Query
}

type Query {
  user: User
}

type User {
  username: String!
  roles: [String!]!
}
"""

    void "multiple methods detected for single field"() {
        given:
            startContext(SCHEMA, SPEC_NAME)

        when:
            getGraphQLBean()

        then:
            def e = thrown(BeanInstantiationException)
            e.cause instanceof MultipleMethodsFoundException
            e.cause.message == """Found multiple methods for one GraphQL field.
  GraphQL object type: User
  GraphQL field: roles
  Methods: 
  1) ${UserResolver.name} roles(${User.name} user)
  2) ${UserResolver.name} roles(${User.name} user, ${DataFetchingEnvironment.name} env)"""
            e.cause.mappingContext.graphQlObjectType == 'User'
            e.cause.mappingContext.graphQlField == 'roles'
            e.cause.mappingContext.mappedClass == null
            e.cause.mappingContext.mappedMethod == null
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

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        List<String> roles(User user) {
            return Collections.emptyList()
        }

        List<String> roles(User user, DataFetchingEnvironment env) {
            return Collections.emptyList()
        }
    }

}
