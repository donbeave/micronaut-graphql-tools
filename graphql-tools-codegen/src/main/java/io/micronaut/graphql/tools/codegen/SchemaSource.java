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
 * The text of one schema file and its name.
 *
 * @param path the path of the file relative to the schema root, with {@code /} separators; it is the origin of the
 *             definitions in the file and decides the order of the files
 * @param text the content
 */
public record SchemaSource(String path, String text) {

    /**
     * Creates a source.
     */
    public SchemaSource {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(text, "text");
    }
}
