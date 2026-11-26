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

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class RootResolverScalarsStandardAsCompletionStageFieldsSpec extends AbstractSpec {

    static final String SPEC_NAME = "RootResolverScalarsStandardAsCompletionStageFieldsSpec"

    @Language("GraphQL")
    static final String SCHEMA1 = """
schema {
  query: Query
}

type Query {
  testString: String
  testBoolean: Boolean
  testInt: Int
  testFloat: Float
  testID: ID
}
"""

    @Language("GraphQL")
    static final String SCHEMA2 = """
schema {
  query: Query
}

type Query {
  testString: String!
  testBoolean: Boolean!
  testInt: Int!
  testFloat: Float!
  testID: ID!
}
"""

    @Unroll
    void "use standard graphql scalars as method return types wrapped with CompletionStage"() {
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
        result.data.testString == 'test'
        result.data.testBoolean == false
        result.data.testInt == Integer.MIN_VALUE
        result.data.testFloat == Float.MIN_VALUE as Double
        result.data.testID == 'id'

        where:
        schema << [SCHEMA1, SCHEMA2]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        CompletionStage<String> testString() {
            return CompletableFuture.completedFuture("test")
        }

        CompletionStage<Boolean> testBoolean() {
            return CompletableFuture.completedFuture(Boolean.FALSE)
        }

        CompletionStage<Integer> testInt() {
            return CompletableFuture.completedFuture(Integer.valueOf(Integer.MIN_VALUE))
        }

        CompletionStage<Float> testFloat() {
            return CompletableFuture.completedFuture(Float.valueOf(Float.MIN_VALUE))
        }

        CompletionStage<String> testID() {
            return CompletableFuture.completedFuture("id")
        }
    }

}
