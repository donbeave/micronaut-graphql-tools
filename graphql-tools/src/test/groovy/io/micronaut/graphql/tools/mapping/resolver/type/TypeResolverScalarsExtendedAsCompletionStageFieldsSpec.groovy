package io.micronaut.graphql.tools.mapping.resolver.type

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class TypeResolverScalarsExtendedAsCompletionStageFieldsSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverScalarsExtendedAsCompletionStageFieldsSpec"

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
  testLong: Long
  testShort: Short
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
  testLong: Long!
  testShort: Short!
  testBigDecimal: BigDecimal!
  testBigInteger: BigInteger!
}
"""


    @Unroll
    void "use extended graphql-java scalars as method return types wrapped with CompletionStage"() {
        given:
            startContext(schema, SPEC_NAME)

        when:
            def result = executeQuery("""
{
    test {
        testLong
        testShort
        testBigDecimal
        testBigInteger
    }
}
""")

        then:
            result.errors.isEmpty()
            result.data.test.testLong == Long.MIN_VALUE
            result.data.test.testShort == Short.MIN_VALUE
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
        CompletionStage<Long> testLong(Test test) {
            return CompletableFuture.completedFuture(Long.MIN_VALUE)
        }

        CompletionStage<Short> testShort(Test test) {
            return CompletableFuture.completedFuture(Short.MIN_VALUE)
        }

        CompletionStage<BigDecimal> testBigDecimal(Test test) {
            return CompletableFuture.completedFuture(BigDecimal.ZERO)
        }

        CompletionStage<BigInteger> testBigInteger(Test test) {
            return CompletableFuture.completedFuture(BigInteger.ONE)
        }
    }

}
