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
package io.micronaut.graphql.tools.internal.manifest;

import graphql.schema.GraphQLSchema;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.manifest.BindingManifest;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.internal.LibraryBeanSelection;
import jakarta.inject.Singleton;
import java.util.NoSuchElementException;

/**
 * Keeps the {@link BindingManifest} that {@link GraphQLFactory} records while it binds the schema.
 */
@Internal
@Singleton
public final class BindingManifestHolder {

    private final BeanContext beanContext;
    private BindingManifest bindingManifest;

    BindingManifestHolder(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    public BindingManifest get() {
        if (bindingManifest == null) {
            LibraryBeanSelection.Provided competitor = LibraryBeanSelection.competitor(beanContext, GraphQLSchema.class);
            if (competitor != null) {
                throw new NoSuchElementException("There is no binding manifest: the library did not bind a schema, because the "
                        + "application provides " + LibraryBeanSelection.describe(competitor) + ", which the library does not "
                        + "bind. Remove that bean, or give it a qualifier such as @Named, to let the library bind its schema");
            }
            throw new NoSuchElementException("There is no binding manifest: the schema is not bound yet, "
                    + "or the manifest is not recorded (set " + GraphQLToolsConfiguration.PREFIX + ".manifest.record to true)");
        }
        return bindingManifest;
    }

    public void set(BindingManifest bindingManifest) {
        this.bindingManifest = bindingManifest;
    }

}
