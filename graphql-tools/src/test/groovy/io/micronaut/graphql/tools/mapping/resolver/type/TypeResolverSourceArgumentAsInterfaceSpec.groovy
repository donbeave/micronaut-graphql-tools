package io.micronaut.graphql.tools.mapping.resolver.type

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language

class TypeResolverSourceArgumentAsInterfaceSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverSourceArgumentAsInterfaceSpec"

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
  avatar: String
}
"""

    void "the source object passed to the GraphQLTypeResolver's method"() {
        given:
            startContext(SCHEMA, SPEC_NAME)

        when:
            def result = executeQuery("""
{ 
    user {
        username
        avatar
    }
}
""")

        then:
            result.errors.isEmpty()
            result.dataPresent
            result.data.user.username == 'test'
            result.data.user.avatar == 'pig.png'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User user() {
            return new UserImpl(username: 'test')
        }
    }

    static interface User {
        String getUsername()
    }

    @GraphQLType(User.class)
    static class UserImpl implements User {
        String username
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        String avatar(User user) {
            assert user != null
            assert user.username == 'test'
            return 'pig.png'
        }
    }

}
