package io.micronaut.graphql.tools.mapping.resolver.type

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language

class TypeResolverIntrospectedEntityAsFieldSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverIntrospectedEntityAsFieldSpec"

    void "use GraphQLTypeResolver for the introspected entity"() {
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
  username: String
  fullName: String
}
"""
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    user {
        username
        fullName
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.user.username == 'test'
        result.data.user.fullName == 'Test User'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User user() {
            return new User()
        }
    }

    @Introspected
    static class User {
        String getUsername() {
            return 'test'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        String fullName(User user) {
            return 'Test User'
        }
    }

}
