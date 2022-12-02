package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.ClassNotIntrospectedException
import org.intellij.lang.annotations.Language

class RootResolverNotIntrospectedClassAsArgumentSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverNotIntrospectedClassAsArgumentSpec"

    void "field's argument points to a not introspected class"() {
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
        e.cause instanceof ClassNotIntrospectedException
        e.cause.message == """The class ${PriceInput.name} is not introspected. Ensure the class is annotated with ${GraphQLInput.name}.
  GraphQL object type: Query
  GraphQL field: price
  GraphQL argument: input
  Mapped class: ${Query.name}
  Mapped method: price(${PriceInput.name} input)"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'price'
        e.cause.mappingContext.graphQlArgument == 'input'
        e.cause.mappingContext.objectTypeDefinition.name == 'Query'
        e.cause.mappingContext.fieldDefinition.name == 'price'
        e.cause.mappingContext.inputValueDefinition.get().name == 'input'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == "price(${PriceInput.name} input)"
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Float price(PriceInput input) {
            return 10.00f
        }
    }

    static class PriceInput {
        String from
        String to
    }

}
