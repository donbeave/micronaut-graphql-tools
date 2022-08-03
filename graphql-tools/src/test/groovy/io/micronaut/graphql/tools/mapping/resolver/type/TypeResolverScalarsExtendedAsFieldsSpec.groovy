package io.micronaut.graphql.tools.mapping.resolver.type

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class TypeResolverScalarsExtendedAsFieldsSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverScalarsExtendedAsFieldsSpec"

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
  testLong1: Long!
  testLong2: Long!
  testShort1: Short!
  testShort2: Short!
  testBigDecimal: BigDecimal!
  testBigInteger: BigInteger!
}
"""


    @Unroll
    void "use extended graphql-java scalars as method return types"() {
        given:
            startContext(schema, SPEC_NAME)

        when:
            def result = executeQuery("""
{
    test {
        testLong1
        testLong2
        testShort1
        testShort2
        testBigDecimal
        testBigInteger
    }
}
""")

        then:
            result.errors.isEmpty()
            result.data.test.testLong1 == Long.MAX_VALUE
            result.data.test.testLong2 == Long.MIN_VALUE
            result.data.test.testShort1 == Short.MAX_VALUE
            result.data.test.testShort2 == Short.MIN_VALUE
            result.data.test.testBigDecimal == BigDecimal.ZERO
            result.data.test.testBigInteger == BigInteger.ONE

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
        long testLong1(Test test) {
            return Long.MAX_VALUE
        }

        Long testLong2(Test test) {
            return Long.MIN_VALUE
        }

        short testShort1(Test test) {
            return Short.MAX_VALUE
        }

        Short testShort2(Test test) {
            return Short.MIN_VALUE
        }

        BigDecimal testBigDecimal(Test test) {
            return BigDecimal.ZERO
        }

        BigInteger testBigInteger(Test test) {
            return BigInteger.ONE
        }
    }

}
