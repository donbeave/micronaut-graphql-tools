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
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class RootResolverScalarsExtendedAsCompletionStageFieldsSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverScalarsExtendedAsCompletionStageFieldsSpec"

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
    testLong
    testShort
    testBigDecimal
    testBigInteger
}
""")

        then:
        result.errors.isEmpty()
        result.data.testLong == Long.MIN_VALUE
        result.data.testShort == Short.MIN_VALUE
        result.data.testBigDecimal == BigDecimal.ZERO
        result.data.testBigInteger == BigInteger.ONE

        where:
        schema << [SCHEMA1, SCHEMA2]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        CompletionStage<Long> testLong() {
            return CompletableFuture.completedFuture(Long.MIN_VALUE)
        }

        CompletionStage<Short> testShort() {
            return CompletableFuture.completedFuture(Short.MIN_VALUE)
        }

        CompletionStage<BigDecimal> testBigDecimal() {
            return CompletableFuture.completedFuture(BigDecimal.ZERO)
        }

        CompletionStage<BigInteger> testBigInteger() {
            return CompletableFuture.completedFuture(BigInteger.ONE)
        }
    }

}
