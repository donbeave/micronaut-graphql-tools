package io.micronaut.graphql.tools.mapping.input

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

class InputIncorrectInputValueMappingListToBuiltInClassSpec extends AbstractTest {

    static final String SPEC_NAME = "InputIncorrectInputValueMappingListToBuiltInClassSpec"

    void "the List input value points to a String property"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  testString(input: TestInput): String
}

input TestInput {
  from: [String]
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The argument is mapped to a built-in class, when required an instance of Iterable interface.
  GraphQL input object type: TestInput
  GraphQL input value: from
  Mapped class: ${TestInput.name}
  Mapped property: from
  Provided class: ${String.name}
  Supported classes: java.lang.Iterable, java.util.Collection, java.util.List, java.util.Set"""
        e.cause.mappingContext.graphQlInputObjectType == 'TestInput'
        e.cause.mappingContext.graphQlInputValue == 'from'
        e.cause.mappingContext.mappedClass == TestInput
        e.cause.mappingContext.mappedProperty == "from"
        e.cause.providedClass == String
        e.cause.supportedClasses == [Iterable, Collection, List, Set] as HashSet
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String testString(TestInput input) {
            return null
        }
    }

    @GraphQLInput
    static class TestInput {
        String from
    }

}
