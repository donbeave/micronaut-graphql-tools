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

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

class RootResolverIncorrectArgumentMappingEnumToCustomClassSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectArgumentMappingEnumToCustomClassSpec"

    void "the enum as a field's argument points to a String parameter"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  displayName(value: Month): String
}

enum Month {
  JANUARY
  FEBRUARY
  MARCH
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """[incorrect-class-mapping] The argument is mapped to a built-in class, when required an enum.
  GraphQL coordinate: Query.displayName(value:) (line 7, column 15)
  GraphQL object type: Query
  GraphQL field: displayName
  GraphQL argument: value
  Mapped class: ${Query.name}
  Mapped method: displayName(java.lang.String value)
  Provided class: java.lang.String"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'displayName'
        e.cause.mappingContext.graphQlArgument == 'value'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == 'displayName(java.lang.String value)'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String displayName(String value) {
            return null
        }
    }

}
