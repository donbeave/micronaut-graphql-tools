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
package io.micronaut.graphql.tools.mapping.input

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import org.intellij.lang.annotations.Language

class InputMethodNotFoundSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverInputMethodNotFoundSpec"

    void "input's property not found"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  hello(input: HelloInput): String
}

input HelloInput {
  firstName: String
  lastName: String
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """[field-not-bound] The property or method `lastName` not found in ${HelloInput.name}'s input.
  GraphQL coordinate: HelloInput.lastName (line 12, column 3)
  GraphQL input object type: HelloInput
  GraphQL input value: lastName
  Mapped class: ${HelloInput.name}"""
        e.cause.mappingContext.graphQlInputObjectType == 'HelloInput'
        e.cause.mappingContext.mappedClass == HelloInput
        e.cause.methodName == 'lastName'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello(HelloInput input) {
            return null
        }
    }

    @GraphQLInput
    static class HelloInput {
        String firstName
    }

}
