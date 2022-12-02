package io.micronaut.graphql.tools.mapping.resolver.type

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class TypeResolverScalarsStandardAsArgumentsSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverScalarsStandardAsArgumentsSpec"

    @Language("GraphQL")
    static final String SCHEMA1 = """
schema {
  query: Query
}

type Query {
  test: Test
}

type Test {
  hello(
    testString: String
    testBoolean1: Boolean
    testBoolean2: Boolean
    testInt1: Int
    testInt2: Int
    testFloat1: Float
    testFloat2: Float
    testID: ID
  ): String
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
  hello(
    testString: String!
    testBoolean1: Boolean!
    testBoolean2: Boolean!
    testInt1: Int!
    testInt2: Int!
    testFloat1: Float!
    testFloat2: Float!
    testID: ID!
  ): String
}
"""


    @Unroll
    void "use standard graphql scalars as method's parameters"() {
        given:
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    test {
        hello(
            testString: "test",
            testBoolean1: true,
            testBoolean2: false,
            testInt1: 123,
            testInt2: -123,
            testFloat1: 1.23,
            testFloat2: -1.23,
            testID: "id"
        )
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.test.hello == 'World'

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
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Test.class)
    static class TestResolver {
        String hello(
                Test test,
                String testString,
                boolean testBoolean1,
                Boolean testBoolean2,
                int testInt1,
                Integer testInt2,
                float testFloat1,
                Float testFloat2,
                String testID
        ) {
            assert testString == 'test'
            assert testBoolean1 == true
            assert testBoolean2 == false
            assert testInt1 == 123
            assert testInt2 == -123
            assert testFloat1 == 1.23f
            assert testFloat2 == -1.23f
            assert testID == 'id'

            return "World"
        }
    }

}
