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
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import org.intellij.lang.annotations.Language

class RootResolverSkipProcessOneTypeTwiceSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverSkipProcessOneTypeTwiceSpec"

    void "skip process one type twice"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  testUser1: User
  testUser2: User
}

type User {
  username: String!
}
"""

        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    testUser1 {
        username
    }
    testUser2 {
        username
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.testUser1.username == 'test1'
        result.data.testUser2.username == 'test2'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User testUser1() {
            return new User(username: 'test1')
        }

        User testUser2() {
            return new User(username: 'test2')
        }
    }

    @GraphQLType
    static class User {
        String username
    }

}
