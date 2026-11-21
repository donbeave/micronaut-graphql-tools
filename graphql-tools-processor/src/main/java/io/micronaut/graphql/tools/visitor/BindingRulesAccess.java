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
package io.micronaut.graphql.tools.visitor;

import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassFindings;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;
import io.micronaut.inject.ast.ClassElement;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Internal: not part of the public API, and not for application code. It runs the per-class binding rules over a compiler
 * {@link ClassElement} the way the visitor does, so that the benchmarks can measure that path without a class in this
 * package. It may change or disappear in any release without notice.
 */
public final class BindingRulesAccess {

    private BindingRulesAccess() {
    }

    /**
     * Reads the schema the way the visitor does.
     *
     * @param directories directories with {@code .graphqls} files
     * @return the schema, or empty when it cannot be read
     */
    public static Optional<SchemaModel> schema(List<Path> directories) {
        return new CheckedSchema(directories).model();
    }

    /**
     * Reads the element through a model of its own and runs the rules of the class, like the visitor does for the first
     * class of a compilation.
     *
     * @param schema the schema to check against
     * @param element a class element within its processing round
     * @return the findings of the class
     */
    public static List<Finding> check(SchemaModel schema, ClassElement element) {
        ElementTypeModel model = new ElementTypeModel(new ModelCoverage());
        return ResolverClassFindings.check(schema, model, model.of(element), false);
    }
}
