package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language

class RootResolverIntrospectedEntityAsFieldSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIntrospectedEntityAsFieldSpec"

    void "field returns an introspected class"() {
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
}
"""
            startContext(schema, SPEC_NAME)

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
            result.data.user.username == 'test'
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

}
