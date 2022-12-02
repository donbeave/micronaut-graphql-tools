package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

class RootResolverIncorrectArgumentMappingBuiltInScalarToNotSupportedClassSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectArgumentMappingBuiltInScalarToNotSupportedClassSpec"

    void "the String field's argument points to an Integer parameter"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  hello(input: String): String
}
"""
        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The argument is mapped to the incorrect class.
  GraphQL object type: Query
  GraphQL field: hello
  GraphQL argument: input
  Mapped class: ${Query.name}
  Mapped method: hello(${Integer.name} input)
  Provided class: ${Integer.name}
  Supported classes: ${String.name}"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'hello'
        e.cause.mappingContext.graphQlArgument == 'input'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == "hello(${Integer.name} input)"
        e.cause.providedClass == Integer
        e.cause.supportedClasses == [String] as HashSet
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello(Integer input) {
            return null
        }
    }

}
