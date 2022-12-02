package io.micronaut.graphql.tools.mapping.resolver.root

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectArgumentCountException
import org.intellij.lang.annotations.Language

class RootResolverTooManyArgumentsExcludeDfeSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverTooManyArgumentsExcludeDfeSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  username: String
}
"""

    void "the method has more arguments (exclude DataFetchingEnvironment)"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectArgumentCountException
        e.cause.message == """The method has too many arguments, provided: 1, required 0 arg(s).
  GraphQL object type: Query
  GraphQL field: username
  Mapped class: ${Query.name}
  Mapped method: username(${String.name} uid, ${DataFetchingEnvironment.name} dfe)"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == "username(${String.name} uid, ${DataFetchingEnvironment.name} dfe)"
        e.cause.providedCount == 1
        e.cause.requiredCount == 0
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String username(String uid, DataFetchingEnvironment dfe) {
            return null
        }
    }

}
