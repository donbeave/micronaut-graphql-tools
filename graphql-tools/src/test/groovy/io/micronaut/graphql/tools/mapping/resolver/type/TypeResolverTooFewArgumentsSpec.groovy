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

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class TypeResolverTooFewArgumentsSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverTooFewArgumentsSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  user: User
}

type User {
  username(uid: ID): String
}
"""

    void "the method has less arguments"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.ARGUMENT_MISMATCH
        e.cause.message == """[argument-mismatch] The method has too few arguments, provided: 1, required 2 arg(s): username(${User.name} user, ID uid)
  GraphQL coordinate: User.username (line 11, column 3)
  Mapped class: ${UserResolver.name}
  Mapped method: username(${User.name} user)"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == UserResolver
        e.cause.mappingContext.mappedMethod == "username(${User.name} user)"
        e.cause.providedCount == 1
        e.cause.requiredCount == 2
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User user() {
            return null
        }
    }

    @GraphQLType
    static class User {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        String username(User user) {
            return null
        }
    }

}
