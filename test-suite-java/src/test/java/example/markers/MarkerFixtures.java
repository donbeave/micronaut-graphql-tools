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
package example.markers;

import graphql.schema.idl.SchemaParser;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer;
import io.micronaut.graphql.tools.resolver.MutationResolver;
import io.micronaut.graphql.tools.resolver.QueryResolver;
import io.micronaut.graphql.tools.resolver.TypeResolver;
import jakarta.inject.Singleton;

// The marker interfaces of resolver contracts (#73): a contract per field or type, the root operation fixed by the marker,
// the source type read from the type argument, and no bean without a scope annotation of the implementation.
@Factory
public class MarkerFixtures {

    public static final String MARKERS = "markers";

    @Bean
    @Singleton
    @Requires(env = MARKERS)
    TypeDefinitionRegistryCustomizer markersSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Shelf { id: ID! slots: Int! label: String! }\n"
                        + "extend type Query { shelf: Shelf stock: String! }\n"
                        + "extend type Mutation { stock: String! }\n"));
    }

    @GraphQLType
    public static class Shelf {

        public String getId() {
            return "1";
        }

        public int getSlots() {
            return 12;
        }

    }

    // tag::contracts[]
    public interface ShelfQueryContract extends QueryResolver {

        Shelf shelf();

    }

    public interface ShelfLabelContract extends TypeResolver<Shelf> {

        String label(Shelf shelf);

    }
    // end::contracts[]

    // tag::implementations[]
    @Singleton
    @Requires(env = MARKERS)
    public static class ShelfQuery implements ShelfQueryContract {

        @Override
        public Shelf shelf() {
            return new Shelf();
        }

        // a public helper is no part of the contract: it binds nothing and does not trip strict mode
        public String describe() {
            return "a shelf";
        }

    }

    @Singleton
    @Requires(env = MARKERS)
    public static class ShelfLabels implements ShelfLabelContract {

        @Override
        public String label(Shelf shelf) {
            return "Shelf " + shelf.getId();
        }

    }

    // a marker on the class itself: the marker fixes the operation, so the same method name binds one field per operation
    @Singleton
    @Requires(env = MARKERS)
    public static class StockQuery implements QueryResolver {

        public String stock() {
            return "queried";
        }

    }

    @Singleton
    @Requires(env = MARKERS)
    public static class StockMutation implements MutationResolver {

        public String stock() {
            return "mutated";
        }

    }

    // no scope annotation, so no bean: the contract carries no bean stereotype
    @Requires(env = MARKERS)
    public static class UnscopedShelfQuery implements ShelfQueryContract {

        @Override
        public Shelf shelf() {
            return new Shelf();
        }

    }
    // end::implementations[]

}
