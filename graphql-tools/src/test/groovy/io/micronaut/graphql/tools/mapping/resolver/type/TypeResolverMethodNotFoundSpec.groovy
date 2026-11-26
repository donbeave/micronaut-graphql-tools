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
package io.micronaut.graphql.tools.mapping.resolver.type

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class TypeResolverMethodNotFoundSpec extends AbstractSpec {

    static final String SPEC_NAME = "TypeResolverMethodNotFoundSpec"

    void "type's property or method not found"() {
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
        e.cause.getCode() == BindingCode.FIELD_NOT_BOUND
        e.cause.message == """[field-not-bound] The property or method `username` not found in ${User.name}'s type or it resolvers: [${UserResolver.name}].
  GraphQL coordinate: User.username (line 11, column 3)"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.methodName == 'username'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query1 {
        User user() {
            return null
        }
    }

    @GraphQLType
    static class User {
        String firstName
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        String lastName(User user) {
            return null
        }
    }

}
