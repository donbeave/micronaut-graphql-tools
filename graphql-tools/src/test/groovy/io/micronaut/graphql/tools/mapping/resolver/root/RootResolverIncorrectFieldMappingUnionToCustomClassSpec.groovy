package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

class RootResolverIncorrectFieldMappingUnionToCustomClassSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectFieldMappingUnionToCustomClassSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  testUnion: PayloadError
}

union PayloadError = SecurityError | ValidationError

type SecurityError {
  code: String!
}

type ValidationError {
  code: Int!
}
"""

    void "the field which returns union points to a method which returns a custom class"() {
        given:
            startContext(SCHEMA, SPEC_NAME)

        when:
            getGraphQLBean()

        then:
            def e = thrown(BeanInstantiationException)
            e.cause instanceof IncorrectClassMappingException
            e.cause.message == """The field is mapped to a custom class, when required an interface.
  GraphQL object type: Query
  GraphQL field: testUnion
  Mapped class: ${Query.name}
  Mapped method: testUnion()
  Provided class: ${SecurityError.name}"""
            e.cause.mappingContext.graphQlObjectType == 'Query'
            e.cause.mappingContext.graphQlField == 'testUnion'
            e.cause.mappingContext.mappedClass == Query
            e.cause.mappingContext.mappedMethod == 'testUnion()'
            e.cause.providedClass == SecurityError
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        SecurityError testUnion() {
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
                    .registerType("ValidationError", ValidationError.class)
        }

    }

}
