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
package io.micronaut.graphql.tools.internal;

import io.micronaut.core.annotation.Internal;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The fields that both a member of the Java type and a type resolver method can bind, and the resolver methods that the
 * property precedence shadowed.
 *
 * @param bindings                the description of both sides, by coordinate
 * @param shadowedResolverMethods the resolver methods that bind no field because a property won
 */
@Internal
record Ambiguities(Map<String, String> bindings, Set<String> shadowedResolverMethods) {

    static final Ambiguities NONE = new Ambiguities(Map.of(), Set.of());

    Ambiguities {
        bindings = Collections.unmodifiableMap(new TreeMap<>(bindings));
        shadowedResolverMethods = Set.copyOf(shadowedResolverMethods);
    }

    Ambiguities plus(Ambiguities other) {
        Map<String, String> mergedBindings = new TreeMap<>(bindings);
        mergedBindings.putAll(other.bindings);
        Set<String> mergedShadowed = new HashSet<>(shadowedResolverMethods);
        mergedShadowed.addAll(other.shadowedResolverMethods);
        return new Ambiguities(mergedBindings, mergedShadowed);
    }
}
