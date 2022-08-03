package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
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
