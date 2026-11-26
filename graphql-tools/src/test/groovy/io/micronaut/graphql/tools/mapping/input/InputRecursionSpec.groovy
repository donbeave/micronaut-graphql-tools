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
package io.micronaut.graphql.tools.mapping.input

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.intellij.lang.annotations.Language

class InputRecursionSpec extends AbstractSpec {

    static final String SPEC_NODE_FIRST = "InputRecursionSpecNodeFirst"
    static final String SPEC_EDGE_FIRST = "InputRecursionSpecEdgeFirst"

    // two input types that refer to each other: whichever the walk reaches first, the other one holds its converter
    @Language("GraphQL")
    static final String SCHEMA_NODE_FIRST = """
schema {
  query: Query
}

type Query {
  graph(root: NodeInput!, edge: EdgeInput): String!
}

input NodeInput {
  name: String!
  edges: [EdgeInput!]
}

input EdgeInput {
  weight: Int!
  target: NodeInput
}
"""

    @Language("GraphQL")
    static final String SCHEMA_EDGE_FIRST = """
schema {
  query: Query
}

type Query {
  graph(edge: EdgeInput, root: NodeInput!): String!
}

input NodeInput {
  name: String!
  edges: [EdgeInput!]
}

input EdgeInput {
  weight: Int!
  target: NodeInput
}
"""

    static final String QUERY = '''
{
    graph(root: {name: "a", edges: [{weight: 1, target: {name: "b", edges: [{weight: 2, target: {name: "c"}}]}}, {weight: 3}]},
          edge: {weight: 4, target: {name: "d", edges: [{weight: 5}]}})
}
'''

    static final String EXPECTED = 'a[1->b[2->c],3] | 4->d[5]'

    void "input types that refer to each other are converted at every level when the node is reached first"() {
        given:
        startContext(SCHEMA_NODE_FIRST, SPEC_NODE_FIRST)

        when:
        def result = executeQuery(QUERY)

        then:
        result.errors.isEmpty()
        result.data.graph == EXPECTED
    }

    void "input types that refer to each other are converted at every level when the edge is reached first"() {
        given:
        startContext(SCHEMA_EDGE_FIRST, SPEC_EDGE_FIRST)

        when:
        def result = executeQuery(QUERY)

        then:
        result.errors.isEmpty()
        result.data.graph == EXPECTED
    }

    @Requires(property = 'spec.name', value = SPEC_NODE_FIRST)
    @Singleton
    @GraphQLRootResolver
    static class NodeFirstQuery {
        String graph(NodeInput root, EdgeInput edge) {
            return "${root.show()} | ${edge.show()}"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_EDGE_FIRST)
    @Singleton
    @GraphQLRootResolver
    static class EdgeFirstQuery {
        String graph(EdgeInput edge, NodeInput root) {
            return "${root.show()} | ${edge.show()}"
        }
    }

    @GraphQLInput
    static class NodeInput {
        String name
        List<EdgeInput> edges

        String show() {
            return edges ? "${name}[${edges.collect { it.show() }.join(',')}]" : name
        }
    }

    @GraphQLInput
    static class EdgeInput {
        int weight
        NodeInput target

        String show() {
            return target ? "${weight}->${target.show()}" : "${weight}"
        }
    }
}
