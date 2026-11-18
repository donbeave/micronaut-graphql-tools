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
import io.micronaut.context.exceptions.BeanContextException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.IncorrectAnnotationException
import org.intellij.lang.annotations.Language

class TypeResolverIncorrectAnnotationSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverIncorrectAnnotationSpec"

    void "the GraphQLTypeResolver annotation used without specify a value class"() {
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
  gravatar: String
}
"""

        when:
        startContext(schema, SPEC_NAME)

        then:
        def e = thrown(BeanContextException)
        e.cause instanceof IncorrectAnnotationException
        e.cause.message == "[incorrect-annotation] Empty value member for @GraphQLTypeResolver annotation in ${UserResolver.class.name} class."
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
        String getUsername() {
            return "abc"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver
    static class UserResolver {
        String gravatar(User user) {
            return null
        }
    }

}
