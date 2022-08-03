package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language

class RootResolverCollectionsAsInputValuesSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverCollectionsAsInputValuesSpec"

    void "different Iterable implementations supported as field arguments"() {
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
  testList: [String]
  testSet: [String]
  testQueue: [String]
  testIterable: [String]
}
"""

            startContext(schema, SPEC_NAME)

        when:
            def result = executeQuery("""
{
    hello(input: {
        testList: ["test1", "test2"],
        testSet: ["test1", "test2"],
        testQueue: ["test1", "test2"],
        testIterable: ["test1", "test2"]
    })
}
""")

        then:
            result.errors.isEmpty()
            result.data.hello == 'World'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello(HelloInput input) {
            assert input.testList == ['test1', 'test2'].toList()
            assert input.testSet == ['test1', 'test2'].toSet()
            assert input.testQueue == new LinkedList<>(['test1', 'test2'])
            assert input.testIterable == ['test1', 'test2']

            return "World"
        }
    }

    @GraphQLInput
    static class HelloInput {
        List<String> testList
        Set<String> testSet
        Queue<String> testQueue
        Iterable<String> testIterable
    }

}
