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

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.serde.annotation.Serdeable
import org.intellij.lang.annotations.Language

class SerdeInputMutationSpec extends AbstractTest {

    static final String SPEC_NAME = "SerdeInputMutationSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
  mutation: Mutation
}

type Query {
  status: String!
}

type Mutation {
  addItem(item: ItemInput!): String!
}

input ItemInput {
  sku: String!
  quantity: Int!
}
"""

    void "the library runs without Jackson databind: an input mutation starts and runs on Micronaut Serialization"() {
        when:
        Class.forName('com.fasterxml.jackson.databind.ObjectMapper')

        then:
        thrown(ClassNotFoundException)

        when:
        startContext(SCHEMA, SPEC_NAME)
        def result = executeQuery('mutation { addItem(item: {sku: "a", quantity: 3}) }')

        then:
        result.errors.isEmpty()
        result.data.addItem == 'a x 3'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String status() {
            return 'ok'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Mutation {
        String addItem(ItemInput item) {
            return "${item.sku} x ${item.quantity}"
        }
    }

    @Serdeable
    @GraphQLInput
    static class ItemInput {
        String sku
        int quantity
    }
}
