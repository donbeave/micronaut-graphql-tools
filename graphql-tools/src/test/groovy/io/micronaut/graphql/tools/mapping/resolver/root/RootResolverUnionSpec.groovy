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
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import org.intellij.lang.annotations.Language

class RootResolverUnionSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverUnionSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  testUnion(securityError: Boolean!): PayloadError
}

union PayloadError = SecurityError | ValidationError

type SecurityError {
  code: String!
}

type ValidationError {
  code: Int!
}
"""

    void "basic union support"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    testUnion(securityError: true) {
        ... on SecurityError {
            securityCode: code
        }
        ... on ValidationError {
            validationCode: code
        }
    }
}
""")

        then:
        result.errors.isEmpty()
        result.dataPresent
        result.data.testUnion.securityCode == 'AUTH'

        when:
        result = executeQuery("""
{
    testUnion(securityError: false) {
        ... on SecurityError {
            securityCode: code
        }
        ... on ValidationError {
            validationCode: code
        }
    }
}
""")

        then:
        result.errors.isEmpty()
        result.dataPresent
        result.data.testUnion.validationCode == 123
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        PayloadError testUnion(boolean securityError) {
            if (securityError) {
                return new SecurityError()
            } else {
                return new ValidationError()
            }
        }
    }

    static interface PayloadError {
    }

    @GraphQLType
    static class SecurityError implements PayloadError {
        String code = "AUTH"
    }

    @GraphQLType
    static class ValidationError implements PayloadError {
        Integer code = 123
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @io.micronaut.context.annotation.Factory
    static class GraphQLFactory {
        @Bean
        @jakarta.inject.Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("SecurityError", SecurityError.class)
                    .registerType("ValidationError", ValidationError.class)
        }
    }

}
