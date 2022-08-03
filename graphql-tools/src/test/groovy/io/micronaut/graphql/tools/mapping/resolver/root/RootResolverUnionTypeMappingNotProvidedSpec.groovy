package io.micronaut.graphql.tools.mapping.resolver.root


import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionary
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.UnionTypeMappingNotProvidedException
import org.intellij.lang.annotations.Language

class RootResolverUnionTypeMappingNotProvidedSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverUnionTypeMappingNotProvidedSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  unionTest: PayloadError
}

union PayloadError = SecurityError | ValidationError

type SecurityError {
  code: String!
}

type ValidationError {
  code: Int!
}
"""

    void "unable to detect representation class"() {
        given:
            startContext(SCHEMA, SPEC_NAME)

        when:
            getGraphQLBean()

        then:
            def e = thrown(BeanInstantiationException)
            e.cause instanceof UnionTypeMappingNotProvidedException
            e.cause.message == """Can not detect representation class for type ValidationError, member of PayloadError union. Ensure the representation class is registered via ${SchemaMappingDictionary.name}.
  GraphQL object type: Query
  GraphQL field: unionTest
  Mapped class: ${Query.name}
  Mapped method: unionTest()"""
            e.cause.mappingContext.graphQlObjectType == 'Query'
            e.cause.mappingContext.graphQlField == 'unionTest'
            e.cause.mappingContext.mappedClass == Query
            e.cause.mappingContext.mappedMethod == 'unionTest()'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        PayloadError unionTest() {
            return null
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
        }

    }

}
