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
        e.cause.message == """[class-not-introspected] The class ${ConversionInput.name} is not introspected. Ensure the class is annotated with ${GraphQLInput.name}.
  GraphQL coordinate: ConversionInput (line 14, column 1)
  GraphQL type: ConversionInput
  Mapped class: ${ConversionInput.name}
  Reached from: PriceInput.conversion (line 11, column 3)"""
        e.cause.mappingContext.coordinate == 'ConversionInput'
        e.cause.mappingContext.mappedClass == ConversionInput
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
