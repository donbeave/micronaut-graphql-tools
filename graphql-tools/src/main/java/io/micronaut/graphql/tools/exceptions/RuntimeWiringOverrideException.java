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
package io.micronaut.graphql.tools.exceptions;

import java.util.List;

/**
 * Thrown when {@link io.micronaut.graphql.tools.RuntimeWiringCustomizer} beans replace a data fetcher, default data
 * fetcher, type resolver or scalar that the library registered, the wiring of a
 * {@link io.micronaut.graphql.tools.SchemaDirective} bean, or the wiring factory, or put a data fetcher or type
 * resolver for something the library wired into a code registry: the replacement would silently undo the binding.
 */
public final class RuntimeWiringOverrideException extends RuntimeException {

    private final List<String> replacements;

    /**
     * @param replacements every replacement, as {@code "<what> (<customizer class>)"}, in the order the customizers ran
     */
    public RuntimeWiringOverrideException(List<String> replacements) {
        super("RuntimeWiringCustomizer beans may only add to the runtime wiring, but they replaced what the library "
                + "wired:\n  "
                + String.join("\n  ", replacements)
                + "\n  Bind these fields with the library, wrap data fetchers with a SchemaDirectiveWiring, or change "
                + "the built schema with a GraphQLSchemaCustomizer.");

        this.replacements = List.copyOf(replacements);
    }

    /**
     * @return every replacement, as {@code "<what> (<customizer class>)"}, in the order the customizers ran
     */
    public List<String> getReplacements() {
        return replacements;
    }

}
