package io.micronaut.graphql.tools.mapping.type

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.IncorrectArgumentCountException
import org.intellij.lang.annotations.Language

class TypeTooFewArgumentsSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeTooFewArgumentsSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  user: User
}

type User {
  username(unmasked: Boolean): String
}
"""

    void "the method has less arguments"() {
        given:
            startContext(SCHEMA, SPEC_NAME)

        when:
            getGraphQLBean()

        then:
            def e = thrown(BeanInstantiationException)
            e.cause instanceof IncorrectArgumentCountException
            e.cause.message == """The method has too few arguments, provided: 0, required 1 arg(s): username(Boolean unmasked)
  GraphQL object type: User
  GraphQL field: username
  Mapped class: ${User.name}
  Mapped method: getUsername()"""
            e.cause.mappingContext.graphQlObjectType == 'User'
            e.cause.mappingContext.graphQlField == 'username'
            e.cause.mappingContext.mappedClass == User
            e.cause.mappingContext.mappedMethod == "getUsername()"
            e.cause.providedCount == 0
            e.cause.requiredCount == 1
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
        String getUsername() {
            return null
        }
    }

}
