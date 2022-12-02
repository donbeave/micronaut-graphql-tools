package io.micronaut.graphql.tools.mapping.type

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import org.intellij.lang.annotations.Language

class TypeDataFetchingEnvironmentSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeDataFetchingEnvironmentSpec"

    @Language("GraphQL")
    static String SCHEMA = """
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

    void "DataFetchingEnvironment passed to a GraphQLType's method"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{ 
    user {
        username
    }
}
""")

        then:
        result.errors.isEmpty()
        result.dataPresent
        result.data.user.username == 'test'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User user(DataFetchingEnvironment env) {
            return new User('test')
        }
    }

    @GraphQLType
    static class User {
        private final String username

        User(String username) {
            this.username = username
        }

        @GraphQLField
        String username(DataFetchingEnvironment env) {
            assert env != null
            assert env.field.name == 'username'
            assert env.parentType.name == 'User'
            return username
        }
    }

}
