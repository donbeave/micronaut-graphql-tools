package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

class RootResolverIncorrectFieldMappingObjectTypeToBuiltInClassSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectFieldMappingObjectTypeToBuiltInClassSpec"

    void "the field which returns object type points to a method which returns an Integer"() {
        given:
            @Language("GraphQL")
            String schema = """
schema {
  query: Query
}

type Query {
  currentUser: User
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
            e.cause.message == """The field is mapped to a built-in class, when required a custom class.
  GraphQL object type: Query
  GraphQL field: currentUser
  Mapped class: ${Query.name}
  Mapped method: currentUser()
  Provided class: ${Integer.name}"""
            e.cause.mappingContext.graphQlObjectType == 'Query'
            e.cause.mappingContext.graphQlField == 'currentUser'
            e.cause.mappingContext.mappedClass == Query
            e.cause.mappingContext.mappedMethod == 'currentUser()'
            e.cause.providedClass == Integer
    }

    void "mapping custom GraphQL type to a wrong class [required field]"() {
        given:
            @Language("GraphQL")
            String schema = """
schema {
  query: Query
}

type Query {
  currentUser: User!
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
            e.cause.message == """The field is mapped to a built-in class, when required a custom class.
  GraphQL object type: Query
  GraphQL field: currentUser
  Mapped class: ${Query.name}
  Mapped method: currentUser()
  Provided class: ${Integer.name}"""
            e.cause.mappingContext.graphQlObjectType == 'Query'
            e.cause.mappingContext.graphQlField == 'currentUser'
            e.cause.mappingContext.mappedClass == Query
            e.cause.mappingContext.mappedMethod == 'currentUser()'
            e.cause.providedClass == Integer
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Integer currentUser() {
            return 0
        }
    }

}
