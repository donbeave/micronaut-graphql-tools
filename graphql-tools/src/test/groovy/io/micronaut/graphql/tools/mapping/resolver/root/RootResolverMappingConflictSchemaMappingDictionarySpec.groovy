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

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.MappingConflictException
import org.intellij.lang.annotations.Language

class RootResolverMappingConflictSchemaMappingDictionarySpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverMappingConflictSchemaMappingDictionarySpec"

    @Language("GraphQL")
    static final String SCHEMA = """
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

    void "attempt map a single type to a different implementations by using SchemaMappingDictionary"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MappingConflictException
        e.cause.message == """[mapping-conflict] Unable to map GraphQL type `User` to ${User.name}, as it is already mapped to ${AnotherUser.name}.
  GraphQL coordinate: Query.user (line 7, column 3)
  GraphQL object type: Query
  GraphQL field: user
  Mapped class: ${Query.name}
  Mapped method: user()"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'user'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == 'user()'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User user() {
            return new User(username: 'test')
        }
    }

    @GraphQLType
    static class User {
        String username
    }

    @GraphQLType
    static class AnotherUser {
        String username
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @io.micronaut.context.annotation.Factory
    static class GraphQLFactory {
        @Bean
        @jakarta.inject.Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("User", AnotherUser.class)
        }
    }

}
