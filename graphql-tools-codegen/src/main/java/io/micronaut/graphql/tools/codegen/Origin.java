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

import java.util.Objects;

/**
 * A position in a schema file. It is used for diagnostics and never written into generated code, so the output does not depend on
 * where a checkout lives.
 *
 * @param path   the path of the schema file, with {@code /} separators
 * @param line   the 1-based line, or 0 when it is not known
 * @param column the 1-based column, or 0 when it is not known
 */
public record Origin(String path, int line, int column) {

    /**
     * Creates an origin.
     */
    public Origin {
        Objects.requireNonNull(path, "path");
    }

    @Override
    public String toString() {
        return line > 0 ? path + ":" + line + ":" + column : path;
    }
}
