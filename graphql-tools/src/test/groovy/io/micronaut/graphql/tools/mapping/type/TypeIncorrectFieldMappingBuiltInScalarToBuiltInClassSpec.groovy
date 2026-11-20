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
package io.micronaut.graphql.tools.mapping.type

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class TypeIncorrectFieldMappingBuiltInScalarToBuiltInClassSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeIncorrectFieldMappingBuiltInScalarToBuiltInClassSpec"

    void "the String field points to a method which returns an Integer"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  user: User
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
        e.cause.message == """[incorrect-class-mapping] The field is mapped to the incorrect class.
  GraphQL coordinate: User.username (line 11, column 3)
  Mapped class: ${User.name}
  Mapped method: getUsername()
  Provided class: ${Integer.name}
  Supported classes: ${String.name}"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == User
        e.cause.mappingContext.mappedMethod == 'getUsername()'
        e.cause.providedClass == Integer
        e.cause.supportedClasses == [String] as HashSet
    }

    void "GraphQL field inside sub-type mapped to the incorrect class [required field]"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  user: User
}

type User {
  username: String!
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.INCORRECT_CLASS_MAPPING
        e.cause.message == """[incorrect-class-mapping] The field is mapped to the incorrect class.
  GraphQL coordinate: User.username (line 11, column 3)
  Mapped class: ${User.name}
  Mapped method: getUsername()
  Provided class: ${Integer.name}
  Supported classes: ${String.name}"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == User
        e.cause.mappingContext.mappedMethod == 'getUsername()'
        e.cause.providedClass == Integer
        e.cause.supportedClasses == [String] as HashSet
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        User user() {
            return null
        }
    }

    @GraphQLType
    static class User {
        Integer getUsername() {
            return 0
        }
    }

}
