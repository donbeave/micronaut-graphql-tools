package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language

class RootResolverScalarsStandardAsInputValuesSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverScalarsStandardAsInputValuesSpec"

    @Language("GraphQL")
    static final String SCHEMA1 = """
schema {
  query: Query
}

type Query {
  hello(input: HelloInput): String
}

input HelloInput {
  testString: String
  testBoolean1: Boolean
  testBoolean2: Boolean
  testInt1: Int
  testInt2: Int
  testFloat1: Float
  testFloat2: Float
  testID: ID
}
"""

    @Language("GraphQL")
    static final String SCHEMA2 = """
schema {
  query: Query
}

type Query {
  hello(input: HelloInput!): String
}

input HelloInput {
  testString: String!
  testBoolean1: Boolean!
  testBoolean2: Boolean!
  testInt1: Int!
  testInt2: Int!
  testFloat1: Float!
  testFloat2: Float!
  testID: ID!
}
"""

    void "use standard graphql scalars as input's properties"() {
        given:
            startContext(schema, SPEC_NAME)

        when:
            def result = executeQuery("""
{
    hello(input: {
        testString: "test",
        testBoolean1: true,
        testBoolean2: false,
        testInt1: 123,
        testInt2: -123,
        testFloat1: 1.23,
        testFloat2: -1.23,
        testID: "id"
    })
}
""")

        then:
            result.errors.isEmpty()
            result.data.hello == 'World'

        where:
            schema << [SCHEMA1, SCHEMA2]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello(HelloInput input) {
            assert input.testString == 'test'
            assert input.testBoolean1 == true
            assert input.testBoolean2 == false
            assert input.testInt1 == 123
            assert input.testInt2 == -123
            assert input.testFloat1 == 1.23f
            assert input.testFloat2 == -1.23f
            assert input.testID == 'id'

            return "World"
        }
    }

    @GraphQLInput
    static class HelloInput {
        String testString
        boolean testBoolean1
        Boolean testBoolean2
        int testInt1
        Integer testInt2
        float testFloat1
        Float testFloat2
        String testID
    }

}
