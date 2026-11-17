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

import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.NonNull;

/**
 * Changes the {@link TypeDefinitionRegistry} before the library binds it, for example to add the directive
 * definitions that a directive library provides.
 *
 * <p>Every bean of this type is called once, with a copy of the application's {@code TypeDefinitionRegistry} bean,
 * which stays unchanged. The beans run lowest order first: by {@link io.micronaut.core.annotation.Order @Order} on
 * the bean class or factory method, or by {@link io.micronaut.core.order.Ordered#getOrder()} if the bean implements
 * {@code Ordered}. The library then binds the changed registry, so a type or field added here needs a binding like
 * any other.</p>
 */
@FunctionalInterface
public interface TypeDefinitionRegistryCustomizer {

    /**
     * Changes the registry in place.
     *
     * @param registry the registry the library will bind
     */
    void customize(@NonNull TypeDefinitionRegistry registry);

}
