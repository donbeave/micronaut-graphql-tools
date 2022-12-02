package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.ImplementationNotFoundException
import org.intellij.lang.annotations.Language

class RootResolverImplementationNotFoundSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverImplementationNotFoundSpec"

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

    void "method returns interface which implementation class is not marked correctly with an annotation"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof ImplementationNotFoundException
        e.cause.message == """Can not find implementation class for the interface ${User.name}.
  GraphQL object type: Query
  GraphQL field: user
  Mapped class: ${Query.name}
  Mapped method: user()"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'user'
        e.cause.mappingContext.objectTypeDefinition.name == 'Query'
        e.cause.mappingContext.fieldDefinition.name == 'user'
        e.cause.mappingContext.inputValueDefinition.isPresent() == false
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == "user()"
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User user() {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    static interface User {

    }

    @GraphQLType
    static class UserImpl implements User {

    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        String username(User user) {
            return null
        }
    }

}
