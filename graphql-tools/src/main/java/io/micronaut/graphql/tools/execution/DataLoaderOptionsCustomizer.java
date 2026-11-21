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

import io.micronaut.core.order.Ordered;
import org.dataloader.DataLoaderOptions;

/**
 * Changes the {@link DataLoaderOptions} of one batch loader bean, each time the loader is created for an execution.
 *
 * <p>The loader is named as in the {@code DataLoaderRegistry}: the {@code @Named} qualifier of the loader bean, or else its
 * simple class name with a lower-case first letter. The customizer applies to that loader and to no other. Several
 * customizers of one loader run in the order of {@link #getOrder()}, lowest first, and each gets the options that the
 * previous one returned. A customizer whose {@link #loaderName()} names no loader bean fails the startup.</p>
 *
 * <p>The options that the first customizer gets already hold the library's default for the operation: caching is off in a
 * loader that a subscription operation creates. A customizer may change that.</p>
 *
 * <p>The library does not call a setter of {@code DataLoaderOptions}, because java-dataloader 3.x and 6.x differ there: the
 * options are mutable in 3.x (a setter returns the same object) and immutable in 6.x (the options are copied with
 * {@code transform}, or {@code DataLoaderOptions.newOptions(options)}). The customizer therefore returns the options that the
 * loader must use, and the library uses what it returns. Write it with the API of the java-dataloader version that the
 * application has. Return a new object each time rather than a shared one when the options hold state, such as a
 * cache map: the loader is created again for every execution.</p>
 */
public interface DataLoaderOptionsCustomizer extends Ordered {

    /**
     * @return the name of the loader that this customizer applies to
     */
    String loaderName();

    /**
     * Customizes the options of one loader.
     *
     * @param options the options so far: the default of the library, or what the previous customizer returned
     * @return the options that the loader uses, never null
     */
    DataLoaderOptions customize(DataLoaderOptions options);

}
