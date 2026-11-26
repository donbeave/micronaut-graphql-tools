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
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class TypeResolverScalarsExtendedAsArgumentsSpec extends AbstractSpec {

    static final String SPEC_NAME = "TypeResolverScalarsExtendedAsArgumentsSpec"

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
  hello(
      testLong1: Long
      testLong2: Long
      testShort1: Short
      testShort2: Short
      testBigDecimal: BigDecimal
      testBigInteger: BigInteger
  ): String
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
  hello(
      testLong1: Long!
      testLong2: Long!
      testShort1: Short!
      testShort2: Short!
      testBigDecimal: BigDecimal!
      testBigInteger: BigInteger
  ): String
}
"""

    @Unroll
    void "use extended graphql-java scalars as method's parameters"() {
        given:
        startContext(schema, SPEC_NAME, config)

        when:
        def result = executeQuery("""
{
    test {
        hello(
            testLong1: 123,
            testLong2: -123,
            testShort1: 1,
            testShort2: -1,
            testBigDecimal: 10.00,
            testBigInteger: 10
        )
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
        String hello(
                Test test,
                long testLong1,
                Long testLong2,
                short testShort1,
                Short testShort2,
                BigDecimal testBigDecimal,
                BigInteger testBigInteger
        ) {
            assert testLong1 == 123
            assert testLong2 == -123
            assert testShort1 == 1
            assert testShort2 == -1
            assert testBigDecimal == BigDecimal.valueOf(10.00)
            assert testBigInteger == BigInteger.valueOf(10)

            return "World"
        }
    }

}
