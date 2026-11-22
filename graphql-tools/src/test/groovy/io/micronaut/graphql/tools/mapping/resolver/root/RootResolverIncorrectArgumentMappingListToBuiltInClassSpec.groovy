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
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class RootResolverIncorrectArgumentMappingListToBuiltInClassSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectArgumentMappingListToBuiltInClassSpec"

    void "the List as a field's argument points to a String parameter"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  testString(input: [String]): String
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.INCORRECT_CLASS_MAPPING
        e.cause.message == """[incorrect-class-mapping] The argument is mapped to a built-in class, when required an instance of Iterable interface.
  GraphQL coordinate: Query.testString(input:) (line 7, column 14)
  GraphQL argument: input
  Mapped class: ${Query.name}
  Mapped method: testString(${String.name} input)
  Provided class: ${String.name}
  Supported classes: java.lang.Iterable, java.util.Collection, java.util.List, java.util.Set"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'testString'
        e.cause.mappingContext.graphQlArgument == 'input'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == "testString(${String.name} input)"
        e.cause.providedClass == String
        e.cause.supportedClasses == [Iterable, Collection, List, Set] as HashSet
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String testString(String input) {
            return null
        }
    }

}
