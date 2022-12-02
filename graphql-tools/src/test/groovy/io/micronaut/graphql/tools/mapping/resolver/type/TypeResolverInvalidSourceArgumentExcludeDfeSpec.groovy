package io.micronaut.graphql.tools.mapping.resolver.type

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.InvalidSourceArgumentException
import org.intellij.lang.annotations.Language

class TypeResolverInvalidSourceArgumentExcludeDfeSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverInvalidSourceArgumentExcludeDfeSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
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

    void "the first argument in the GraphQLTypeResolver's method should be the source instance"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidSourceArgumentException
        e.cause.message == """The source argument must be instance of ${User.name} class, provided: ${String.name}.
  GraphQL object type: User
  GraphQL field: username
  Mapped class: ${UserResolver.name}
  Mapped method: username(${String.name} uid, ${DataFetchingEnvironment.name} dfe)"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == UserResolver
        e.cause.mappingContext.mappedMethod == "username(${String.name} uid, ${DataFetchingEnvironment.name} dfe)"
        e.cause.providedClass == String
        e.cause.requiredClass == User
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query2 {
        User user() {
            return null
        }
    }

    @GraphQLType
    static class User {

    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        String username(String uid, DataFetchingEnvironment dfe) {
            return null
        }
    }

}
