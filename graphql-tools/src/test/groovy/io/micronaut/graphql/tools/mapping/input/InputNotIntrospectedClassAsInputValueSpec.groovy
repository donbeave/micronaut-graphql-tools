package io.micronaut.graphql.tools.mapping.input

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.ClassNotIntrospectedException
import org.intellij.lang.annotations.Language

class InputNotIntrospectedClassAsInputValueSpec extends AbstractTest {

    static final String SPEC_NAME = "InputNotIntrospectedClassAsInputValueSpec"

    void "input object type refers to the class without GraphQLInput annotation"() {
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
  conversion: ConversionInput!
}

input ConversionInput {
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
        e.cause.message == """The class ${ConversionInput.name} is not introspected. Ensure the class is annotated with ${GraphQLInput.name}.
  GraphQL input object type: PriceInput
  GraphQL input value: conversion
  Mapped class: ${PriceInput.name}
  Mapped property: conversion"""
        e.cause.mappingContext.graphQlInputObjectType == 'PriceInput'
        e.cause.mappingContext.graphQlInputValue == 'conversion'
        e.cause.mappingContext.inputObjectTypeDefinition.name == 'PriceInput'
        e.cause.mappingContext.inputValueDefinition.name == 'conversion'
        e.cause.mappingContext.mappedClass == PriceInput
        e.cause.mappingContext.mappedProperty == 'conversion'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Float price(PriceInput input) {
            return 10.00f
        }
    }

    @GraphQLInput
    static class PriceInput {
        ConversionInput conversion
    }

    static class ConversionInput {
        String from
        String to
    }

}
