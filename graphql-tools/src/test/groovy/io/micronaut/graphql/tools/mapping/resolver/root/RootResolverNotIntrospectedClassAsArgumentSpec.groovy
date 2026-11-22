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
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.binding.BindingCode
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException

class RootResolverNotIntrospectedClassAsArgumentSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverNotIntrospectedClassAsArgumentSpec"

    void "field's argument points to a not introspected class"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  price(input: PriceInput): Float
}

input PriceInput {
  from: String!
  to: String!
}
"""
        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.CLASS_NOT_INTROSPECTED
        e.cause.message == """[class-not-introspected] The class ${PriceInput.name} is not introspected. Ensure the class is annotated with ${GraphQLInput.name}. A class that cannot carry it, such as a generated or external one, is listed (or its package) in @GraphQLModels.
  GraphQL coordinate: PriceInput (line 10, column 1)
  Mapped class: ${PriceInput.name}
  Reached from: Query.price(input:) (line 7, column 9)"""
        e.cause.getCode() == BindingCode.CLASS_NOT_INTROSPECTED
        e.cause.mappingContext.coordinate == 'PriceInput'
        e.cause.mappingContext.mappedClass == PriceInput
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        Float price(PriceInput input) {
            return 10.00f
        }
    }

    static class PriceInput {
        String from
        String to
    }

}
