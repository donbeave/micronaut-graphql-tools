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
package io.micronaut.graphql.tools.binding.internal.model;

import io.micronaut.core.annotation.Internal;

/**
 * The type model that the per-class rules read: one implementation per adapter (the runtime reads
 * {@code BeanDefinition} and {@code BeanIntrospection}, the visitor reads {@code ClassElement}). An instance caches per
 * instance and is not shared between compilations or application contexts.
 */
@Internal
public interface TypeModel {

    /**
     * @param ref a reference that was read from an already resolved {@link JClass}
     * @return the class it names
     */
    JClass resolve(TypeRef ref);
}
