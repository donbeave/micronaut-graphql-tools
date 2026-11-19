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

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

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
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.INCORRECT_CLASS_MAPPING
        e.cause.message == """[incorrect-class-mapping] The field is mapped to a custom class, when required an interface.
  GraphQL coordinate: Query.testUnion (line 7, column 3)
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
