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
package io.micronaut.graphql.tools.execution;

import graphql.schema.DataFetcher;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

/**
 * Wraps the data fetcher of selected fields, for cross-cutting logic such as sanitising arguments. A bean of this type
 * is asked once for each field at startup, never per request: it returns the fetcher it received, the very same
 * instance, for a field it does not select, and a wrapper for a field it does.
 *
 * <p>The library does not offer a field that has no arguments, nor a trivial property fetcher, unless the decorator opts
 * in with {@link #decoratesFieldsWithoutArguments()} or {@link #decoratesTrivialFields()}. A field that no decorator
 * selects keeps its base fetcher unchanged, so graphql-java's shortcuts for trivial fetchers still apply. Prefer this
 * SPI to a {@code SchemaDirective} with an {@code everyElement} wiring, which wraps every field: such a wiring must
 * return the element unchanged unless it carries its directive.</p>
 *
 * <p>Decorators wrap outermost. The composition of a field, outermost first, is: the decorators, in the order of
 * {@link io.micronaut.core.annotation.Order @Order} on the bean class or of
 * {@link io.micronaut.core.order.Ordered#getOrder()} (lowest first, so the lowest is the outermost), the async
 * boundary, the executor hop of {@code @ExecuteOn}, the publisher adapter, the method or property fetcher.</p>
 *
 * <p>Implement {@link io.micronaut.core.order.Ordered} or use {@code @Order}; lower runs first.</p>
 */
@Experimental
public interface GraphQLFieldFetcherDecorator {

    /**
     * @return whether the library offers this decorator the fields that the schema declares without arguments; false by default
     */
    default boolean decoratesFieldsWithoutArguments() {
        return false;
    }

    /**
     * @return whether the library offers this decorator the fields served by a property, whose fetcher is trivial; false by default
     */
    default boolean decoratesTrivialFields() {
        return false;
    }

    /**
     * Decides, once, how to serve a field.
     *
     * @param field   what is known about the field
     * @param fetcher the fetcher of the field with the inner decorators and the library's wrappers applied
     * @return {@code fetcher} itself to leave the field alone, or a fetcher that wraps it; never null
     */
    @NonNull
    DataFetcher<Object> decorate(@NonNull FieldDescriptor field, @NonNull DataFetcher<Object> fetcher);
}
