package io.micronaut.graphql.tools.mapping.type

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class TypeScalarsStandardAsFieldsSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeScalarsStandardAsFieldsSpec"

    @Language("GraphQL")
    static final String SCHEMA1 = """
schema {
  query: Query
}

type Query {
  test: Test
}

type Test {
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
  test: Test
}

type Test {
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

    @Unroll
    void "use standard graphql scalars as method return types"() {
        given:
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    test {
        testString
        testBoolean1
        testBoolean2
        testInt1
        testInt2
        testFloat1
        testFloat2
        testID
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.test.testString == 'test'
        result.data.test.testBoolean1 == true
        result.data.test.testBoolean2 == false
        result.data.test.testInt1 == Integer.MAX_VALUE
        result.data.test.testInt2 == Integer.MIN_VALUE
        result.data.test.testFloat1 == Float.MAX_VALUE as Double
        result.data.test.testFloat2 == Float.MIN_VALUE as Double
        result.data.test.testID == 'id'

        where:
        schema << [SCHEMA1, SCHEMA2]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Test test() {
            return new Test()
        }
    }

    @GraphQLType
    static class Test {
        String testString = "test"
        boolean testBoolean1 = true
        Boolean testBoolean2 = Boolean.FALSE
        int testInt1 = Integer.MAX_VALUE
        Integer testInt2 = Integer.valueOf(Integer.MIN_VALUE)
        float testFloat1 = Float.MAX_VALUE
        Float testFloat2 = Float.valueOf(Float.MIN_VALUE)
        String testID = "id"
    }

}
