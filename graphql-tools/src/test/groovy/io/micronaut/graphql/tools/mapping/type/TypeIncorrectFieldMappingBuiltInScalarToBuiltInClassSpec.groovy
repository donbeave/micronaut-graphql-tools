package io.micronaut.graphql.tools.mapping.type

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

class TypeIncorrectFieldMappingBuiltInScalarToBuiltInClassSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeIncorrectFieldMappingBuiltInScalarToBuiltInClassSpec"

    void "the String field points to a method which returns an Integer"() {
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
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The field is mapped to the incorrect class.
  GraphQL object type: User
  GraphQL field: username
  Mapped class: ${User.name}
  Mapped method: getUsername()
  Provided class: ${Integer.name}
  Supported classes: ${String.name}"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == User
        e.cause.mappingContext.mappedMethod == 'getUsername()'
        e.cause.providedClass == Integer
        e.cause.supportedClasses == [String] as HashSet
    }

    void "GraphQL field inside sub-type mapped to the incorrect class [required field]"() {
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
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The field is mapped to the incorrect class.
  GraphQL object type: User
  GraphQL field: username
  Mapped class: ${User.name}
  Mapped method: getUsername()
  Provided class: ${Integer.name}
  Supported classes: ${String.name}"""
        e.cause.mappingContext.graphQlObjectType == 'User'
        e.cause.mappingContext.graphQlField == 'username'
        e.cause.mappingContext.mappedClass == User
        e.cause.mappingContext.mappedMethod == 'getUsername()'
        e.cause.providedClass == Integer
        e.cause.supportedClasses == [String] as HashSet
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
        Integer getUsername() {
            return 0
        }
    }

}
