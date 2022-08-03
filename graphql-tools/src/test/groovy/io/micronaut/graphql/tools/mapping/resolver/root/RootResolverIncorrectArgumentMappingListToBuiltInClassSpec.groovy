package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

class RootResolverIncorrectArgumentMappingListToBuiltInClassSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectArgumentMappingListToBuiltInClassSpec"

    void "the List as a field's argument points to a String parameter"() {
        given:
            @Language("GraphQL")
            String schema = """
schema {
  query: Query
}

type Query {
  testString(input: [String]): String
}
"""

            startContext(schema, SPEC_NAME)

        when:
            getGraphQLBean()

        then:
            def e = thrown(BeanInstantiationException)
            e.cause instanceof IncorrectClassMappingException
            e.cause.message == """The argument is mapped to a built-in class, when required an instance of Iterable interface.
  GraphQL object type: Query
  GraphQL field: testString
  GraphQL argument: input
  Mapped class: ${Query.name}
  Mapped method: testString(${String.name} input)
  Provided class: ${String.name}
  Supported classes: java.lang.Iterable, java.util.Collection, java.util.List, java.util.Set"""
            e.cause.mappingContext.graphQlObjectType == 'Query'
            e.cause.mappingContext.graphQlField == 'testString'
            e.cause.mappingContext.graphQlArgument == 'input'
            e.cause.mappingContext.mappedClass == Query
            e.cause.mappingContext.mappedMethod == "testString(${String.name} input)"
            e.cause.providedClass == String
            e.cause.supportedClasses == [Iterable, Collection, List, Set] as HashSet
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String testString(String input) {
            return null
        }
    }

}
