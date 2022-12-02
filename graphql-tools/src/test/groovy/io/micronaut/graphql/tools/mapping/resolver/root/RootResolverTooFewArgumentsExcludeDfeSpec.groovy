package io.micronaut.graphql.tools.mapping.resolver.root

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectArgumentCountException
import org.intellij.lang.annotations.Language

class RootResolverTooFewArgumentsExcludeDfeSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverTooFewArgumentsExcludeDfeSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  username(uid: ID): String
}
"""

    void "the method has less arguments (exclude DataFetchingEnvironment)"() {
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
  Mapped class: ${Query.name}
  Mapped method: username(${DataFetchingEnvironment.name} dfe)"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == "username(${DataFetchingEnvironment.name} dfe)"
        e.cause.providedCount == 0
        e.cause.requiredCount == 1
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String username(DataFetchingEnvironment dfe) {
            return null
        }
    }

}
