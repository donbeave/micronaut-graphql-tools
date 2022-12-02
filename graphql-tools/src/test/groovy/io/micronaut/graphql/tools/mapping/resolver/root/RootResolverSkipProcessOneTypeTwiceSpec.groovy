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
