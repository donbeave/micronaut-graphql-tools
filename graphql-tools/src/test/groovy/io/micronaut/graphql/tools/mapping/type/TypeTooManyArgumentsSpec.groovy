package io.micronaut.graphql.tools.mapping.type

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.IncorrectArgumentCountException
import org.intellij.lang.annotations.Language

class TypeTooManyArgumentsSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeTooManyArgumentsSpec"

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

    void "the method has more arguments"() {
        given:
            startContext(SCHEMA, SPEC_NAME)

        when:
            getGraphQLBean()

        then:
            def e = thrown(BeanInstantiationException)
            e.cause instanceof IncorrectArgumentCountException
            e.cause.message == """The method has too many arguments, provided: 1, required 0 arg(s).
  GraphQL object type: User
  GraphQL field: username
  Mapped class: ${User.name}
  Mapped method: username(${String.name} uid)"""
            e.cause.mappingContext.graphQlObjectType == 'User'
            e.cause.mappingContext.graphQlField == 'username'
            e.cause.mappingContext.mappedClass == User
            e.cause.mappingContext.mappedMethod == "username(${String.name} uid)"
            e.cause.providedCount == 1
            e.cause.requiredCount == 0
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User user() {
            return null
        }
    }

    @GraphQLType
    static class User {
        @GraphQLField
        String username(String uid) {
            return null
        }
    }

}
