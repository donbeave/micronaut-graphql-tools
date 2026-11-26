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

class RootResolverScalarsStandardAsFieldsSpec extends AbstractSpec {

    static final String SPEC_NAME = "RootResolverScalarsStandardAsFieldsSpec"

    @Language("GraphQL")
    static final String SCHEMA1 = """
schema {
  query: Query
}

type Query {
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
    testString
    testBoolean1
    testBoolean2
    testInt1
    testInt2
    testFloat1
    testFloat2
    testID
}
""")

        then:
        result.errors.isEmpty()
        result.data.testString == 'test'
        result.data.testBoolean1 == true
        result.data.testBoolean2 == false
        result.data.testInt1 == Integer.MAX_VALUE
        result.data.testInt2 == Integer.MIN_VALUE
        result.data.testFloat1 == Float.MAX_VALUE as Double
        result.data.testFloat2 == Float.MIN_VALUE as Double
        result.data.testID == 'id'

        where:
        schema << [SCHEMA1, SCHEMA2]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String testString() {
            return "test"
        }

        boolean testBoolean1() {
            return true
        }

        Boolean testBoolean2() {
            return Boolean.FALSE
        }

        int testInt1() {
            return Integer.MAX_VALUE
        }

        Integer testInt2() {
            return Integer.valueOf(Integer.MIN_VALUE)
        }

        float testFloat1() {
            return Float.MAX_VALUE
        }

        Float testFloat2() {
            return Float.valueOf(Float.MIN_VALUE)
        }

        String testID() {
            return "id"
        }
    }

}
