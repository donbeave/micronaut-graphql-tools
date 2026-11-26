/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.mapping.resolver.type

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class TypeResolverScalarsStandardAsInputValuesSpec extends AbstractSpec {

    static final String SPEC_NAME = "TypeResolverScalarsStandardAsInputValuesSpec"

    @Language("GraphQL")
    static final String SCHEMA1 = """
schema {
  query: Query
}

type Query {
  test: Test
}

type Test {
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
  test: Test
}

type Test {
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


    @Unroll
    void "use standard graphql scalars as method return types"() {
        given:
        startContext(schema, SPEC_NAME, config)

        when:
        def result = executeQuery("""
{
    test {
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
}
""")

        then:
        result.errors.isEmpty()
        result.data.test.hello == 'World'

        where:
        schema  | config
        SCHEMA1 | ['micronaut.graphql.tools.strict': false]
        SCHEMA2 | [:]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
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
    @Singleton
    @GraphQLTypeResolver(Test.class)
    static class TestResolver {
        String hello(Test test, HelloInput input) {
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
