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
import io.micronaut.core.annotation.Nullable;

import java.util.List;
import java.util.Set;

/**
 * What the marker interfaces ({@code QueryResolver}, {@code MutationResolver}, {@code SubscriptionResolver} and
 * {@code TypeResolver<T>}) fix for a resolver class. The visitor computes it from the hierarchy of the class and writes it
 * into the annotation metadata; the startup reads it from there.
 *
 * @param operations      the root operations ({@code query}, {@code mutation}, {@code subscription}) that the markers of
 *                        the class name, sorted; empty when no marker names one, which leaves every root type open
 * @param sourceType      the binary name of the type argument of {@code TypeResolver<T>}, or {@code null}
 * @param contractMethods the names of the methods of the class that implement a method of a contract interface (an
 *                        interface that extends a marker); {@code null} when the class implements no contract interface
 *                        that declares a method, so every public method is a candidate
 */
@Internal
public record ResolverMarkers(List<String> operations, @Nullable String sourceType,
                              @Nullable Set<String> contractMethods) {

    /** A class that no marker touches. */
    public static final ResolverMarkers NONE = new ResolverMarkers(List.of(), null, null);

    /**
     * @param name the name of a method
     * @return whether a marker contract leaves the method a candidate for binding
     */
    public boolean admits(String name) {
        return contractMethods == null || contractMethods.contains(name);
    }
}
