/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.mapping.resolver.root

import jakarta.inject.Singleton
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
    @Singleton
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
