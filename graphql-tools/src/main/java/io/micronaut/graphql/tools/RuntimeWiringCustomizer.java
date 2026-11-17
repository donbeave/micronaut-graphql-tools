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
package io.micronaut.graphql.tools;

import graphql.schema.idl.RuntimeWiring;
import io.micronaut.core.annotation.NonNull;

/**
 * Adds to the {@link RuntimeWiring} the library generated from its bindings: scalars, directive wirings, field
 * visibility, a comparator registry, and so on.
 *
 * <p>Every bean of this type is called once, after the library has wired every field it binds and before the schema
 * is generated. The beans run lowest order first: by {@link io.micronaut.core.annotation.Order @Order} on the bean
 * class or factory method, or by {@link io.micronaut.core.order.Ordered#getOrder()} if the bean implements
 * {@code Ordered}.</p>
 *
 * <p>A customizer may only add. The startup fails with
 * {@link io.micronaut.graphql.tools.exceptions.RuntimeWiringOverrideException}, which names every replacement and the
 * customizer that made it, if a customizer replaces a data fetcher, default data fetcher, type resolver or scalar that
 * the library registered, the wiring of a {@link SchemaDirective} bean, or the wiring factory, or puts a data fetcher
 * or type resolver for a field or type the library wired into a code registry ({@code RuntimeWiring.Builder#codeRegistry}; graphql-java's schema generator lets
 * the code registry win). Such a replacement would silently undo the binding. To wrap data fetchers use a
 * {@link graphql.schema.idl.SchemaDirectiveWiring}; to change the generated schema use a
 * {@link GraphQLSchemaCustomizer} (graphql-java 25 removed {@code RuntimeWiring.Builder#transformer}).</p>
 *
 * <p>Allowed: code registry entries for fields and types the library does not wire (such as those of an allowed
 * unreachable type), and enum value providers ({@code TypeRuntimeWiring.Builder#enumValues}), which the library does
 * not register.</p>
 *
 * <p>graphql-java 25.1's builder already rejects the first replaced data fetcher, default data fetcher, type resolver
 * or scalar itself ({@code StrictModeWiringException}); graphql-java 21.3 accepts them silently. The library's check
 * covers both lines, the wiring factory included, and lists every replacement at once where the builder lets the
 * customizers finish.</p>
 */
@FunctionalInterface
public interface RuntimeWiringCustomizer {

    /**
     * Adds to the runtime wiring.
     *
     * @param builder the builder that holds the library's wiring
     */
    void customize(@NonNull RuntimeWiring.Builder builder);

}
