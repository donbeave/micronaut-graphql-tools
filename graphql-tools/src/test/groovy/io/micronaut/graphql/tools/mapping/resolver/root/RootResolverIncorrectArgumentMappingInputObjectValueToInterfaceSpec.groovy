package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

class RootResolverIncorrectArgumentMappingInputObjectValueToInterfaceSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectArgumentMappingInputObjectValueToInterfaceSpec"

    void "the input object type as a field's argument points to an interface"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  price(input: PriceInput): Float
}

input PriceInput {
  from: String!
  to: String!
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The argument is mapped to an interface, when required a custom class.
  GraphQL object type: Query
  GraphQL field: price
  GraphQL argument: input
  Mapped class: ${Query.name}
  Mapped method: price(${PriceInput.name} input)
  Provided class: ${PriceInput.name}"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'price'
        e.cause.mappingContext.graphQlArgument == 'input'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == "price(${PriceInput.name} input)"
        e.cause.providedClass == PriceInput
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Float price(PriceInput input) {
            return 10.00f
        }
    }

    @GraphQLInput
    static interface PriceInput {
        String from
        String to
    }

}
