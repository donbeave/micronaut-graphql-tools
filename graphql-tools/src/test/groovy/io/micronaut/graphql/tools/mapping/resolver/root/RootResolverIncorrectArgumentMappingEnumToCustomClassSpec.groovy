package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

class RootResolverIncorrectArgumentMappingEnumToCustomClassSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectArgumentMappingEnumToCustomClassSpec"

    void "the enum as a field's argument points to a String parameter"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  displayName(value: Month): String
}

enum Month {
  JANUARY
  FEBRUARY
  MARCH
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The argument is mapped to a built-in class, when required an enum.
  GraphQL object type: Query
  GraphQL field: displayName
  GraphQL argument: value
  Mapped class: ${Query.name}
  Mapped method: displayName(java.lang.String value)
  Provided class: java.lang.String"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'displayName'
        e.cause.mappingContext.graphQlArgument == 'value'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == 'displayName(java.lang.String value)'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String displayName(String value) {
            return null
        }
    }

}
