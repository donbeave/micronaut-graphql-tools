package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectArgumentCountException
import org.intellij.lang.annotations.Language

class RootResolverTooFewArgumentsSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverTooFewArgumentsSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  username(uid: ID): String
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
            e.cause.message == """The method has too few arguments, provided: 0, required 1 arg(s): username(ID uid)
  GraphQL object type: Query
  GraphQL field: username
  Mapped class: ${Query1.name}
  Mapped method: username()"""
            e.cause.mappingContext.graphQlObjectType == 'Query'
            e.cause.mappingContext.graphQlField == 'username'
            e.cause.mappingContext.mappedClass == Query1
            e.cause.mappingContext.mappedMethod == 'username()'
            e.cause.providedCount == 0
            e.cause.requiredCount == 1
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query1 {
        String username() {
            return null
        }
    }

}
