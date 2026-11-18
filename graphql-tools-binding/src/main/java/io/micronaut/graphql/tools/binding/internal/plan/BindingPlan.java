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
package io.micronaut.graphql.tools.binding.internal.plan;

import io.micronaut.core.annotation.Internal;

import java.util.List;
import java.util.function.Supplier;

/**
 * What the binding phase decided, as immutable records: the facts that the binding manifest shows. The texts that need
 * the live types of the runtime ({@link Entry}) are suppliers, read when the manifest is rendered, so that binding a
 * schema pays for none of them. {@code ManifestRenderer} renders a plan.
 *
 * @param entries     one entry per type, field, enum value, input field, abstract member and scalar the binding met
 * @param diagnostics the diagnostic lines (starting with {@code !}), as {@code ManifestRenderer.diagnostic} wrote them
 * @param ignored     the ignored-member lines (starting with {@code ~ignored}), as {@code ManifestRenderer.ignored}
 *                    wrote them
 */
@Internal
public record BindingPlan(List<Entry> entries, List<String> diagnostics, List<String> ignored) {

    /** The text of a column that has no value. */
    public static final String NONE = "-";

    public BindingPlan {
        entries = List.copyOf(entries);
        diagnostics = List.copyOf(diagnostics);
        ignored = List.copyOf(ignored);
    }

    /**
     * @param subject       what the line is about: a type, a coordinate with its signature, or an enum value
     * @param kind          how it is bound ({@code object}, {@code root}, {@code resolver}, {@code enumValue}, ...)
     * @param member        the Java member or type that serves it, or {@link #NONE}
     * @param declaredType  the declared Java type, or {@link #NONE}
     * @param convertedType the Java type after the return wrappers are peeled, or {@link #NONE}
     */
    public record Entry(String subject, String kind, Supplier<String> member, Supplier<String> declaredType,
                        Supplier<String> convertedType) {

        /**
         * @param subject what the line is about
         * @param kind    how it is bound
         * @param member  the Java member or type that serves it
         * @return an entry with no declared and no converted type
         */
        public static Entry of(String subject, String kind, Supplier<String> member) {
            return new Entry(subject, kind, member, () -> NONE, () -> NONE);
        }
    }
}
