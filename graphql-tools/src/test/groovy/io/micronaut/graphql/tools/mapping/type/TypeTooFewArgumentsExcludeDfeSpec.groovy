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

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.IncorrectArgumentCountException
import org.intellij.lang.annotations.Language

class TypeTooFewArgumentsExcludeDfeSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeTooFewArgumentsExcludeDfeSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  user: User
}

type User {
  username(unmasked: Boolean): String
}
"""

    void "the method has less arguments (exclude DataFetchingEnvironment)"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectArgumentCountException
        e.cause.message == """[argument-mismatch] The method has too few arguments, provided: 0, required 1 arg(s): username(Boolean unmasked)
  GraphQL coordinate: User.username (line 11, column 3)
  Mapped class: ${User.name}
  Mapped method: username(${DataFetchingEnvironment.name} dfe)"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == User
        e.cause.mappingContext.mappedMethod == "username(${DataFetchingEnvironment.name} dfe)"
        e.cause.providedCount == 0
        e.cause.requiredCount == 1
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
        @GraphQLField
        String username(DataFetchingEnvironment dfe) {
            return null
        }
    }

}
