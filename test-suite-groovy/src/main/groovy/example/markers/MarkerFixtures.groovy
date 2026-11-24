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
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.resolver.MutationResolver
import io.micronaut.graphql.tools.resolver.QueryResolver
import io.micronaut.graphql.tools.resolver.TypeResolver
import jakarta.inject.Singleton

// The marker interfaces of resolver contracts (#73): a contract per field or type, the root operation fixed by the marker,
// the source type read from the type argument, and no bean without a scope annotation of the implementation.
@CompileStatic
@Factory
class MarkerFixtures {

    static final String MARKERS = 'markers'

    // The same SDL as src/main/resources/markers/markers.graphqls, which the compile-time checks read with the schema of the
    // application: the markers are checked at compile time against the fields that they bind.
    @Bean
    @Singleton
    @Requires(env = MarkerFixtures.MARKERS)
    GraphQLTypeDefinitionRegistryCustomizer markersSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'type Shelf { id: ID! slots: Int! label: String! }\n' +
                            'type Crate { id: ID! tag: String! }\n' +
                            'extend type Query { shelf: Shelf stock: String! crate: Crate tag: String! }\n' +
                            'extend type Mutation { stock: String! }\n'))
        } as GraphQLTypeDefinitionRegistryCustomizer
    }

    @GraphQLType
    static class Shelf {
        String id = '1'
        int slots = 12
    }

    // tag::contracts[]
    static interface ShelfQueryContract extends QueryResolver {

        Shelf shelf()
    }

    static interface ShelfLabelContract extends TypeResolver<Shelf> {

        String label(Shelf shelf)
    }
    // end::contracts[]

    // tag::implementations[]
    @Singleton
    @Requires(env = MarkerFixtures.MARKERS)
    static class ShelfQuery implements ShelfQueryContract {

        @Override
        Shelf shelf() {
            return new Shelf()
        }

        // a public helper is no part of the contract: it binds nothing and does not trip strict mode
        String describe() {
            return 'a shelf'
        }
    }

    @Singleton
    @Requires(env = MarkerFixtures.MARKERS)
    static class ShelfLabels implements ShelfLabelContract {

        @Override
        String label(Shelf shelf) {
            return 'Shelf ' + shelf.id
        }
    }

    // a marker on the class itself: the marker fixes the operation, so the same method name binds one field per operation
    @Singleton
    @Requires(env = MarkerFixtures.MARKERS)
    static class StockQuery implements QueryResolver {

        String stock() {
            return 'queried'
        }
    }

    @Singleton
    @Requires(env = MarkerFixtures.MARKERS)
    static class StockMutation implements MutationResolver {

        String stock() {
            return 'mutated'
        }
    }

    // no scope annotation, so no bean: the contract carries no bean stereotype
    @Requires(env = MarkerFixtures.MARKERS)
    static class UnscopedShelfQuery implements ShelfQueryContract {

        @Override
        Shelf shelf() {
            return new Shelf()
        }
    }
    // end::implementations[]

    @GraphQLType
    static class Crate {
        String id = '7'
    }

    static interface CrateQueryContract extends QueryResolver {

        Crate crate()

        String tag()
    }

    static interface CrateTagContract extends TypeResolver<Crate> {

        String tag(Crate crate)
    }

    // one bean with a root contract and a type contract: each method binds on the side of the contract that declares it,
    // although the root field and the type field share the name tag
    @Singleton
    @Requires(env = MarkerFixtures.MARKERS)
    static class CrateResolvers implements CrateQueryContract, CrateTagContract {

        @Override
        Crate crate() {
            return new Crate()
        }

        @Override
        String tag() {
            return 'root tag'
        }

        @Override
        String tag(Crate crate) {
            return 'tag of ' + crate.id
        }
    }
}
