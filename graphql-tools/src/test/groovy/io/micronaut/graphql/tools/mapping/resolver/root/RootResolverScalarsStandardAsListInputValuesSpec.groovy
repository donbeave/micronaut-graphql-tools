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
package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class RootResolverScalarsStandardAsListInputValuesSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverScalarsStandardAsListInputValuesSpec"

    @Language("GraphQL")
    static final String SCHEMA1 = """
schema {
  query: Query
}

type Query {
  hello(input: HelloInput): String
}

input HelloInput {
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
  hello(input: HelloInput): String
}

input HelloInput {
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
  hello(input: HelloInput): String
}

input HelloInput {
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
  hello(input: HelloInput): String
}

input HelloInput {
  testString: [String!]!
  testBoolean: [Boolean!]!
  testInt: [Int!]!
  testFloat: [Float!]!
  testID: [ID!]!
}
"""


    @Unroll
    void "use standard graphql scalars as input's List properties"() {
        given:
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    hello(input: {
        testString: ["test"],
        testBoolean: [false, true],
        testInt: [-123, 123],
        testFloat: [-1.23, 1.23],
        testID: ["id"]
    })
}
""")

        then:
        result.errors.isEmpty()
        result.data.hello == 'World'

        where:
        schema << [SCHEMA1, SCHEMA2, SCHEMA3, SCHEMA4]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello(HelloInput input) {
            assert input.testString == ['test']
            assert input.testBoolean == [false, true]
            assert input.testInt == [-123, 123]
            assert input.testFloat == [-1.23f, 1.23f]
            assert input.testID == ['id']

            return "World"
        }
    }

    @GraphQLInput
    static class HelloInput {
        List<String> testString
        List<Boolean> testBoolean
        List<Integer> testInt
        List<Float> testFloat
        List<String> testID
    }

}
