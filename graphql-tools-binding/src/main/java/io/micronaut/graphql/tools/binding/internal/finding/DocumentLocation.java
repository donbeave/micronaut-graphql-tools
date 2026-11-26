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
package io.micronaut.graphql.tools.binding.internal.finding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * A position in a client document: a line of a file of the client directory, or a line of the document that an annotation
 * holds inline.
 *
 * @param path   the path of the file, or {@code null} for the document of an annotation
 * @param line   the line, from 1
 * @param column the column, from 1
 */
@Internal
public record DocumentLocation(@Nullable String path, int line, int column) {

    /** The name that stands for the path of a document that an annotation holds. */
    public static final String INLINE = "inline";

    @Override
    public String toString() {
        return (path == null ? INLINE : path) + ":" + line + ":" + column;
    }
}
