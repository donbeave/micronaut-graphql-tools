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
package example.relay

import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import jakarta.inject.Singleton

// The connection types of one list, next to the schema of the example
const val RELAY = "relay-connection"

@Factory
class RelayFixtures {

    // tag::schema[]
    @Bean
    @Singleton
    @Requires(env = [RELAY])
    fun connectionSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Article { id: ID! title: String! }\n" +
                    "type ArticleEdge { cursor: String! node: Article! }\n" +
                    "type PageInfo {\n" +
                    "  hasNextPage: Boolean!\n" +
                    "  hasPreviousPage: Boolean!\n" +
                    "  startCursor: String\n" +
                    "  endCursor: String\n" +
                    "}\n" +
                    "type ArticleConnection { edges: [ArticleEdge!]! pageInfo: PageInfo! totalCount: Int! }\n" +
                    "extend type Query { articles(first: Int!, after: String): ArticleConnection! }\n"
            )
        )
    }
    // end::schema[]
}
