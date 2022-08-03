package io.micronaut.graphql.tools.mapping.resolver.root

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language

class RootResolverDataFetchingEnvironmentSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverDataFetchingEnvironmentSpec"

    @Language("GraphQL")
    static String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
}
"""

    void "DataFetchingEnvironment passed to a GraphQLRootResolver's method"() {
        given:
            startContext(SCHEMA, SPEC_NAME)

        when:
            def result = executeQuery("""
{ 
    hello
}
""")

        then:
            result.errors.isEmpty()
            result.dataPresent
            result.data.hello == 'World'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello(DataFetchingEnvironment env) {
            assert env != null
            assert env.field.name == 'hello'
            assert env.parentType.name == 'Query'
            return 'World'
        }
    }

}
