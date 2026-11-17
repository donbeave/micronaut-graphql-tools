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

import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

import java.util.NoSuchElementException;

/**
 * Keeps the {@link BindingManifest} that {@link GraphQLFactory} records while it binds the schema.
 */
@Internal
@Singleton
public final class BindingManifestHolder {

    private BindingManifest bindingManifest;

    /**
     * @return the binding manifest
     * @throws NoSuchElementException if the schema is not bound yet, or never is because the application
     *                                provides its own {@code GraphQLSchema}
     */
    BindingManifest get() {
        if (bindingManifest == null) {
            throw new NoSuchElementException("The schema is not bound yet, or the application provides its own "
                    + "GraphQLSchema bean, which the library does not bind");
        }
        return bindingManifest;
    }

    void set(BindingManifest bindingManifest) {
        this.bindingManifest = bindingManifest;
    }

}
