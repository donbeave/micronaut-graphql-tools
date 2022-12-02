package io.micronaut.graphql.tools.mapping.resolver.root

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException
import org.intellij.lang.annotations.Language

class RootResolverMultipleMethodsFoundSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverMultipleMethodsFoundSpec"

    @Language("GraphQL")
    static String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
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
  GraphQL object type: Query
  GraphQL field: hello
  Methods: 
  1) ${Query.name} hello()
  2) ${Query.name} hello(${DataFetchingEnvironment.name} env)"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'hello'
        e.cause.mappingContext.mappedClass == null
        e.cause.mappingContext.mappedMethod == null
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return null
        }

        String hello(DataFetchingEnvironment env) {
            return null
        }
    }

}
