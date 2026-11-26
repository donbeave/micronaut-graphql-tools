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
import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class TypeResolverTooManyArgumentsExcludeDfeSpec extends AbstractSpec {

    static final String SPEC_NAME = "TypeResolverTooManyArgumentsExcludeDfeSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
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

    void "the method has more arguments (exclude DataFetchingEnvironment)"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.ARGUMENT_MISMATCH
        e.cause.message == """[argument-mismatch] The method has too many arguments, provided: 2, required 1 arg(s): username(${User.name} user)
  GraphQL coordinate: User.username (line 11, column 3)
  Mapped class: ${UserResolver.name}
  Mapped method: username(${User.name} user, ${String.name} uid, ${DataFetchingEnvironment.name} dfe)"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == UserResolver
        e.cause.mappingContext.mappedMethod == "username(${User.name} user, ${String.name} uid, ${DataFetchingEnvironment.name} dfe)"
        e.cause.providedCount == 2
        e.cause.requiredCount == 1
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

    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        String username(User user, String uid, DataFetchingEnvironment dfe) {
            return null
        }
    }

}
