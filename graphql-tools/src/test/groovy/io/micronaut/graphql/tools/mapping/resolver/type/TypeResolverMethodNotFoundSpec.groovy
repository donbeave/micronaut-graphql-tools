package io.micronaut.graphql.tools.mapping.resolver.type

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import org.intellij.lang.annotations.Language

class TypeResolverMethodNotFoundSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverMethodNotFoundSpec"

    void "type's property or method not found"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  user: User
}

type User {
  username: String
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """The property or method `username` not found in ${User.name}'s type or it resolvers: [${UserResolver.name}].
  GraphQL object type: User
  GraphQL field: username"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.methodName == 'username'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query1 {
        User user() {
            return null
        }
    }

    @GraphQLType
    static class User {
        String firstName
    }

    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        String lastName(User user) {
            return null
        }
    }

}
