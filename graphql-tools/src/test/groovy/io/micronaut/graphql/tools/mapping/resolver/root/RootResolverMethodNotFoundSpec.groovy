package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import org.intellij.lang.annotations.Language

class RootResolverMethodNotFoundSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverMethodNotFoundSpec"

    void "Query's method not found"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  hello: String
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """The method `hello` not found in any root resolvers: [${Query1.name}, ${Query2.name}].
  GraphQL object type: Query
  GraphQL field: hello"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'hello'
        e.cause.methodName == 'hello'
    }

    void "Mutation's method not found"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  mutation: Mutation
}

type Mutation {
  hello: String
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """The method `hello` not found in any root resolvers: [${Query1.name}, ${Query2.name}].
  GraphQL object type: Mutation
  GraphQL field: hello"""
        e.cause.mappingContext.graphQlObjectType == 'Mutation'
        e.cause.mappingContext.graphQlField == 'hello'
        e.cause.methodName == 'hello'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query1 {
        String test1() {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query2 {
        String test2() {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Mutation {
        // ignored as no methods was found inside this class
    }

}
