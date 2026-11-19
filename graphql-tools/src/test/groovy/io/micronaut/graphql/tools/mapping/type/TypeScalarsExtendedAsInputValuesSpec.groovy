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
package io.micronaut.graphql.tools.mapping.type

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class TypeScalarsExtendedAsInputValuesSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeScalarsExtendedAsInputValuesSpec"

    @Language("GraphQL")
    static final String SCHEMA1 = """
scalar Long
scalar Short
scalar BigDecimal
scalar BigInteger

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
  testLong1: Long
  testLong2: Long
  testShort1: Short
  testShort2: Short
  testBigDecimal: BigDecimal
  testBigInteger: BigInteger
}
"""

    @Language("GraphQL")
    static final String SCHEMA2 = """
scalar Long
scalar Short
scalar BigDecimal
scalar BigInteger

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
  testLong1: Long!
  testLong2: Long!
  testShort1: Short!
  testShort2: Short!
  testBigDecimal: BigDecimal!
  testBigInteger: BigInteger!
}
"""

    @Unroll
    void "use extended graphql-java scalars as input's properties"() {
        given:
        startContext(schema, SPEC_NAME, config)

        when:
        def result = executeQuery("""
{
    test {
        hello(input: {
            testLong1: 123,
            testLong2: -123,
            testShort1: 1,
            testShort2: -1,
            testBigDecimal: 10.00,
            testBigInteger: 10
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
    @GraphQLRootResolver
    static class Query {
        Test test() {
            return new Test()
        }
    }

    @GraphQLType
    static class Test {
        @GraphQLField
        String hello(HelloInput input) {
            assert input.testLong1 == 123
            assert input.testLong2 == -123
            assert input.testShort1 == 1
            assert input.testShort2 == -1
            assert input.testBigDecimal == BigDecimal.valueOf(10.00)
            assert input.testBigInteger == BigInteger.valueOf(10)

            return "World"
        }
    }

    @GraphQLInput
    static class HelloInput {
        long testLong1
        Long testLong2
        short testShort1
        Short testShort2
        BigDecimal testBigDecimal
        BigInteger testBigInteger
    }

}
