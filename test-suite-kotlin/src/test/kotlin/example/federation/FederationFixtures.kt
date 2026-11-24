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
package example.federation

import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import jakarta.inject.Singleton

// The schema of one subgraph, next to the schema of the example
const val FEDERATION = "federation-subgraph"

@Factory
class FederationFixtures {

    // tag::schema[]
    @Bean
    @Singleton
    @Requires(env = [FEDERATION])
    fun subgraphSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "directive @key(fields: String!) repeatable on OBJECT | INTERFACE\n" +
                    "type Product @key(fields: \"id\") { id: ID! name: String! }\n" +
                    "type Order @key(fields: \"id\") { id: ID! total: Int! }\n" +
                    "extend type Query { product(id: ID!): Product }\n"
            )
        )
    }
    // end::schema[]

    // tag::entities[]
    @Bean
    @Singleton
    @Requires(env = [FEDERATION])
    fun federation(): GraphQLSchemaCustomizer {
        val entities = mapOf<String, (Map<String, Any?>) -> Any>(
            "Product" to { key -> Product(key["id"] as String, "Product ${key["id"]}") },
            "Order" to { key -> mapOf("__typename" to "Order", "id" to key["id"], "total" to 42) }
        )
        return GraphQLSchemaCustomizer { schema -> FederationTransform.apply(schema, entities) }
    }
    // end::entities[]
}
