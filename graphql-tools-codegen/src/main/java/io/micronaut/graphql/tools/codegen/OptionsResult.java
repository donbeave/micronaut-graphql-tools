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
package io.micronaut.graphql.tools.codegen;

import java.util.List;

/**
 * The outcome of {@link Options#fromMap(java.util.Map)}: the options that could be read, and a diagnostic for each key that could
 * not. An option whose value is invalid has its default in {@code options}.
 *
 * @param options     the options, with the default for every key that is absent or invalid
 * @param diagnostics the findings, in a stable order
 */
public record OptionsResult(Options options, List<Diagnostic> diagnostics) {

    /**
     * Creates a result with an immutable copy of the diagnostics.
     */
    public OptionsResult {
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Whether a diagnostic is an error.
     *
     * @return {@code true} when some key could not be read
     */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }
}
