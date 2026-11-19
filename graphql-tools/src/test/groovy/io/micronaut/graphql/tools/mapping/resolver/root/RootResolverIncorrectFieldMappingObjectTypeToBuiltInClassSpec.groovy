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
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class RootResolverIncorrectFieldMappingObjectTypeToBuiltInClassSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectFieldMappingObjectTypeToBuiltInClassSpec"

    void "the field which returns object type points to a method which returns an Integer"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  currentUser: User
}

type User {
  username: String
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.INCORRECT_CLASS_MAPPING
        e.cause.message == """[incorrect-class-mapping] The field is mapped to a built-in class, when required a custom class.
  GraphQL coordinate: Query.currentUser (line 7, column 3)
  Mapped class: ${Query.name}
  Mapped method: currentUser()
  Provided class: ${Integer.name}"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'currentUser'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == 'currentUser()'
        e.cause.providedClass == Integer
    }

    void "mapping custom GraphQL type to a wrong class [required field]"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  currentUser: User!
}

type User {
  username: String
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.INCORRECT_CLASS_MAPPING
        e.cause.message == """[incorrect-class-mapping] The field is mapped to a built-in class, when required a custom class.
  GraphQL coordinate: Query.currentUser (line 7, column 3)
  Mapped class: ${Query.name}
  Mapped method: currentUser()
  Provided class: ${Integer.name}"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'currentUser'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == 'currentUser()'
        e.cause.providedClass == Integer
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Integer currentUser() {
            return 0
        }
    }

}
