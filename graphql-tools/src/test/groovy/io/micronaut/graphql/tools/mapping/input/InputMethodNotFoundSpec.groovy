package io.micronaut.graphql.tools.mapping.input

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import org.intellij.lang.annotations.Language

class InputMethodNotFoundSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverInputMethodNotFoundSpec"

    void "input's property not found"() {
        given:
            @Language("GraphQL")
            String schema = """
schema {
  query: Query
}

type Query {
  hello(input: HelloInput): String
}

input HelloInput {
  firstName: String
  lastName: String
}
"""

            startContext(schema, SPEC_NAME)

        when:
            getGraphQLBean()

        then:
            def e = thrown(BeanInstantiationException)
            e.cause instanceof MethodNotFoundException
            e.cause.message == """The property or method `lastName` not found in ${HelloInput.name}'s input.
  GraphQL input object type: HelloInput
  GraphQL input value: lastName
  Mapped class: ${HelloInput.name}"""
            e.cause.mappingContext.graphQlInputObjectType == 'HelloInput'
            e.cause.mappingContext.mappedClass == HelloInput
            e.cause.methodName == 'lastName'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello(HelloInput input) {
            return null
        }
    }

    @GraphQLInput
    static class HelloInput {
        String firstName
    }

}
