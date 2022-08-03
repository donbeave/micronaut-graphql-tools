package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language

class RootResolverCollectionsAsFieldsSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverCollectionsAsFieldsSpec"

    void "different Iterable implementations supported as field return types"() {
        given:
            @Language("GraphQL")
            String schema = """
schema {
  query: Query
}

type Query {
  testList: [String]
  testSet: [String]
  testQueue: [String]
  testIterable: [String]
  testIterator: [String]
}
"""

            startContext(schema, SPEC_NAME)

        when:
            def result = executeQuery("""
{
    testList
    testSet
    testQueue
    testIterable
    testIterator
}
""")

        then:
            result.errors.isEmpty()
            result.data.testList == ['test1', 'test2']
            result.data.testSet == ['test1', 'test2']
            result.data.testQueue == ['test1', 'test2']
            result.data.testIterable == ['test1', 'test2']
            result.data.testIterator == ['test1', 'test2']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        List<String> testList() {
            return ["test1", "test2"].toList()
        }

        Set<String> testSet() {
            return ["test1", "test2"].toSet()
        }

        Queue<String> testQueue() {
            return new LinkedList<>(["test1", "test2"])
        }

        Iterable<String> testIterable() {
            return ["test1", "test2"]
        }

        Iterator<String> testIterator() {
            return ["test1", "test2"].iterator()
        }
    }

}
