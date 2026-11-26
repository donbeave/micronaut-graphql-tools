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
import graphql.ExecutionInput
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.context.annotation.Requires
import org.intellij.lang.annotations.Language

class RecordInputSpec extends AbstractSpec {

    static final String SPEC_NAME = "RecordInputSpec"
    static final Map DETECT = [:]

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  plain(input: Plain!): String!
  order(input: Order!): String!
}

input Plain {
  name: String!
  count: Int!
  limit: Int
}

input Line {
  sku: String!
  note: String
}

input Order {
  id: ID!
  first: Line!
  lines: [Line!]!
  memo: String
}
"""

    void "a record input is built through its canonical constructor"() {
        given:
        startContext(SCHEMA, SPEC_NAME, DETECT)

        when:
        def given = executeQuery('{ plain(input: {name: "a", count: 2, limit: 3}) }')
        def omitted = executeQuery('{ plain(input: {name: "b", count: 1}) }')
        def explicitNull = executeQuery('{ plain(input: {name: "c", count: 1, limit: null}) }')

        then:
        given.errors.isEmpty()
        given.data.plain == 'a 2 3'
        omitted.data.plain == 'b 1 null'
        explicitNull.data.plain == 'c 1 null'
    }

    void "an ArgumentValue record component is omitted, null or present, also in nested records and lists of records"() {
        given:
        startContext(SCHEMA, SPEC_NAME, DETECT)

        when:
        def given = executeQuery('{ order(input: {id: "1", first: {sku: "a", note: "n"}, lines: [{sku: "b"}, {sku: "c", note: null}, {sku: "d", note: "m"}], memo: "x"}) }')
        def omitted = executeQuery('{ order(input: {id: "2", first: {sku: "a"}, lines: []}) }')
        def explicitNull = executeQuery('{ order(input: {id: "3", first: {sku: "a", note: null}, lines: [], memo: null}) }')
        def variables = graphQLBean.execute(ExecutionInput.newExecutionInput()
                .query('query($line: Line!) { order(input: {id: "4", first: $line, lines: [$line]}) }')
                .variables([line: [sku: 's']]).build())

        then:
        given.errors.isEmpty()
        given.data.order == 'id=1 first=a:value(n) lines=[b:omitted, c:null, d:value(m)] memo=value(x)'
        omitted.data.order == 'id=2 first=a:omitted lines=[] memo=omitted'
        explicitNull.data.order == 'id=3 first=a:null lines=[] memo=null'
        variables.data.order == 'id=4 first=s:omitted lines=[s:omitted] memo=omitted'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String plain(JavaRecordInputs.Plain input) {
            return "${input.name()} ${input.count()} ${input.limit()}"
        }

        String order(JavaRecordInputs.Order input) {
            String lines = input.lines().collect { "${it.sku()}:${JavaRecordInputs.show(it.note())}" }.join(', ')
            return "id=${input.id()} first=${input.first().sku()}:${JavaRecordInputs.show(input.first().note())} " +
                    "lines=[${lines}] memo=${JavaRecordInputs.show(input.memo())}"
        }
    }

}
