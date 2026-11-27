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
package io.micronaut.graphql.tools.codegen.schema;

import io.micronaut.graphql.tools.codegen.Diagnostic;
import io.micronaut.graphql.tools.codegen.ir.Ir;

import java.util.List;

/**
 * The outcome of {@link IrBuilder}.
 *
 * @param ir           the IR; {@link Ir#EMPTY} when a diagnostic is an error, because a partial IR would mislead an emitter
 * @param diagnostics  the findings of the options, the loader and the rules, in that order; the findings of a step are in a stable
 *                     order that does not depend on the order of the input
 * @param skippedFiles the relative paths of the schema files that hold no schema definition, only executable definitions (client
 *                     operations) or only comments, in the order of their paths; they are not an error
 */
public record IrResult(Ir ir, List<Diagnostic> diagnostics, List<String> skippedFiles) {

    /**
     * Creates a result with immutable copies of the lists.
     */
    public IrResult {
        diagnostics = List.copyOf(diagnostics);
        skippedFiles = List.copyOf(skippedFiles);
    }

    /**
     * Whether a diagnostic is an error.
     *
     * @return {@code true} when there is no usable IR
     */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }
}
