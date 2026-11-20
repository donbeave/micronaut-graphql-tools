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
package example.markers

import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.resolver.MutationResolver
import io.micronaut.graphql.tools.resolver.QueryResolver
import io.micronaut.graphql.tools.resolver.TypeResolver
import jakarta.inject.Singleton

// The marker interfaces of resolver contracts (#73): a contract per field or type, the root operation fixed by the marker,
// the source type read from the type argument, and no bean without a scope annotation of the implementation.
const val MARKERS = "markers"

@Factory
class MarkerFixtures {

    // The same SDL as src/main/resources/markers/markers.graphqls, which the compile-time checks read with the schema of the
    // application: the markers are checked at compile time against the fields that they bind.
    @Bean
    @Singleton
    @Requires(env = [MARKERS])
    fun markersSchema() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Shelf { id: ID! slots: Int! label: String! }\n" +
                    "extend type Query { shelf: Shelf stock: String! }\n" +
                    "extend type Mutation { stock: String! }\n"
            )
        )
    }
}

@GraphQLType
class Shelf {
    val id: String = "1"
    val slots: Int = 12
}

// tag::contracts[]
interface ShelfQueryContract : QueryResolver {

    fun shelf(): Shelf
}

interface ShelfLabelContract : TypeResolver<Shelf> {

    fun label(shelf: Shelf): String
}
// end::contracts[]

// tag::implementations[]
@Singleton
@Requires(env = [MARKERS])
open class ShelfQuery : ShelfQueryContract {

    override fun shelf(): Shelf = Shelf()

    // a public helper is no part of the contract: it binds nothing and does not trip strict mode
    open fun describe(): String = "a shelf"
}

@Singleton
@Requires(env = [MARKERS])
open class ShelfLabels : ShelfLabelContract {

    override fun label(shelf: Shelf): String = "Shelf " + shelf.id
}

// a marker on the class itself: the marker fixes the operation, so the same method name binds one field per operation
@Singleton
@Requires(env = [MARKERS])
open class StockQuery : QueryResolver {

    open fun stock(): String = "queried"
}

@Singleton
@Requires(env = [MARKERS])
open class StockMutation : MutationResolver {

    open fun stock(): String = "mutated"
}

// no scope annotation, so no bean: the contract carries no bean stereotype
@Requires(env = [MARKERS])
open class UnscopedShelfQuery : ShelfQueryContract {

    override fun shelf(): Shelf = Shelf()
}
// end::implementations[]
