package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class RootResolverScalarsStandardAsListFieldsSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverScalarsStandardAsListFieldsSpec"

    @Language("GraphQL")
    static final String SCHEMA1 = """
schema {
  query: Query
}

type Query {
  testString: [String]
  testBoolean: [Boolean]
  testInt: [Int]
  testFloat: [Float]
  testID: [ID]
}
"""

    @Language("GraphQL")
    static final String SCHEMA2 = """
schema {
  query: Query
}

type Query {
  testString: [String]!
  testBoolean: [Boolean]!
  testInt: [Int]!
  testFloat: [Float]!
  testID: [ID]!
}
"""

    @Language("GraphQL")
    static final String SCHEMA3 = """
schema {
  query: Query
}

type Query {
  testString: [String!]
  testBoolean: [Boolean!]
  testInt: [Int!]
  testFloat: [Float!]
  testID: [ID!]
}
"""

    @Language("GraphQL")
    static final String SCHEMA4 = """
schema {
  query: Query
}

type Query {
  testString: [String]!
  testBoolean: [Boolean]!
  testInt: [Int]!
  testFloat: [Float]!
  testID: [ID]!
}
"""

    @Unroll
    void "use standard graphql scalars as List method return types"() {
        given:
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    testString
    testBoolean
    testInt
    testFloat
    testID
}
""")

        then:
        result.errors.isEmpty()
        result.data.testString == ['test1', 'test2']
        result.data.testBoolean == [false, true]
        result.data.testInt == [Integer.MIN_VALUE, Integer.MAX_VALUE]
        result.data.testFloat == [Float.MIN_VALUE as Double, Float.MAX_VALUE as Double]
        result.data.testID == ['id1', 'id2']

        where:
        schema << [SCHEMA1, SCHEMA2, SCHEMA3, SCHEMA4]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        List<String> testString() {
            return ["test1", "test2"]
        }

        List<Boolean> testBoolean() {
            return [Boolean.FALSE, Boolean.TRUE]
        }

        List<Integer> testInt() {
            return [Integer.MIN_VALUE, Integer.MAX_VALUE]
        }

        List<Float> testFloat() {
            return [Float.MIN_VALUE, Float.MAX_VALUE]
        }

        List<String> testID() {
            return ["id1", "id2"]
        }
    }

}
