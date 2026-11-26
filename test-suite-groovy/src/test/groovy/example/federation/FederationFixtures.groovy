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

import graphql.GraphQLContext
import graphql.schema.idl.SchemaParser
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import jakarta.inject.Singleton

import java.util.function.Predicate

@CompileStatic
@Factory
class FederationFixtures {

    // The schema of one subgraph, next to the schema of the example
    static final String FEDERATION = 'federation-subgraph'

    static final String UNGUARDED = 'federation-unguarded'

    // tag::schema[]
    @Bean
    @Singleton
    @Requires(env = FederationFixtures.FEDERATION)
    GraphQLTypeDefinitionRegistryCustomizer subgraphSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'directive @key(fields: String!) repeatable on OBJECT | INTERFACE\n' +
                            'type Product @key(fields: "id") { id: ID! name: String! }\n' +
                            'type Order @key(fields: "id") { id: ID! total: Int! }\n' +
                            'extend type Query { product(id: ID!): Product }\n'))
        } as GraphQLTypeDefinitionRegistryCustomizer
    }
    // end::schema[]

    // tag::entities[]
    @Bean
    @Singleton
    @Requires(env = FederationFixtures.FEDERATION)
    @Order(Ordered.LOWEST_PRECEDENCE)
    GraphQLSchemaCustomizer federation() {
        Map<String, FederationTransform.Entity> entities = [
                Product: new FederationTransform.Entity(
                        { Map<String, Object> key -> new ProductEntity(id: key.id as String, name: "Product ${key.id}".toString()) },
                        FederationAccess.role('ROLE_ADMIN')),
                Order  : new FederationTransform.Entity(
                        { Map<String, Object> key -> [__typename: 'Order', id: key.id, total: 42] },
                        FederationAccess.role('ROLE_BILLING'))
        ]
        return { schema ->
            FederationTransform.apply(schema, entities, FederationAccess.role('ROLE_ADMIN'))
        } as GraphQLSchemaCustomizer
    }
    // end::entities[]

    // The same subgraph with every entity open to every caller: what the guard of the entities above prevents
    @Bean
    @Singleton
    @Requires(env = FederationFixtures.FEDERATION)
    @Requires(env = FederationFixtures.UNGUARDED)
    @Replaces(bean = GraphQLSchemaCustomizer, factory = FederationFixtures)
    @Order(Ordered.LOWEST_PRECEDENCE)
    GraphQLSchemaCustomizer unguardedFederation() {
        Predicate<GraphQLContext> everyone = { GraphQLContext context -> true } as Predicate<GraphQLContext>
        Map<String, FederationTransform.Entity> entities = [
                Product: new FederationTransform.Entity(
                        { Map<String, Object> key -> new ProductEntity(id: key.id as String, name: "Product ${key.id}".toString()) },
                        everyone)
        ]
        return { schema -> FederationTransform.apply(schema, entities, everyone) } as GraphQLSchemaCustomizer
    }

}
