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
import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class TypeTooManyArgumentsExcludeDfeSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeTooManyArgumentsExcludeDfeSpec"

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
        e.cause.message == """[argument-mismatch] The method has too many arguments, provided: 1, required 0 arg(s).
  GraphQL coordinate: User.username (line 11, column 3)
  Mapped class: ${User.name}
  Mapped method: username(${String.name} uid, ${DataFetchingEnvironment.name} dfe)"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == User
        e.cause.mappingContext.mappedMethod == "username(${String.name} uid, ${DataFetchingEnvironment.name} dfe)"
        e.cause.providedCount == 1
        e.cause.requiredCount == 0
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
        @GraphQLField
        String username(String uid, DataFetchingEnvironment dfe) {
            return null
        }
    }

}
