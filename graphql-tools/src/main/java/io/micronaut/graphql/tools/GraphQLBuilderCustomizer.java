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

import graphql.GraphQL;
import io.micronaut.core.annotation.NonNull;

/**
 * Configures the {@link GraphQL} bean the library creates: instrumentation, a
 * {@link graphql.execution.preparsed.PreparsedDocumentProvider}, execution strategies, the default
 * {@link graphql.execution.DataFetcherExceptionHandler}, and so on.
 *
 * <p>Every bean of this type is called once with the builder, before the library builds the {@code GraphQL} bean.
 * The beans run lowest order first: by {@link io.micronaut.core.annotation.Order @Order} on the bean class or factory
 * method, or by {@link io.micronaut.core.order.Ordered#getOrder()} if the bean implements {@code Ordered}. A builder
 * setter such as {@code instrumentation(...)} keeps one value, so a later customizer replaces what an earlier one set
 * there; chain instrumentations with {@link graphql.execution.instrumentation.ChainedInstrumentation}.</p>
 *
 * <p>An application that provides its own {@code GraphQL} bean gets none from the library, so these beans are not
 * called; such an application configures its builder itself. The library never calls
 * {@code GraphQL.Builder#doNotAddDefaultInstrumentations()}, which graphql-java 25 removed.</p>
 */
@FunctionalInterface
public interface GraphQLBuilderCustomizer {

    /**
     * Configures the builder.
     *
     * @param builder the builder of the library's {@code GraphQL} bean, created with the library's schema
     */
    void customize(@NonNull GraphQL.Builder builder);

}
