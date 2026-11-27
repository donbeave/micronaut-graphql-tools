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
package io.micronaut.graphql.tools.codegen.ir.shared;

import java.util.Objects;

/**
 * The documentation of a schema element: its description and its deprecation. The description is the SDL description, or the
 * {@code #} comment before the definition when the {@code commentsAsDescriptions} option is on. Both texts are already escaped
 * for a Javadoc, KDoc or Groovydoc comment (the README of the module lists the escapes), once, so an emitter writes
 * them as they are and a description that holds the comment terminator cannot break the build.
 *
 * @param description the escaped description, or {@code null} when there is none
 * @param deprecation the deprecation, or {@code null} when the element is not deprecated
 */
public record Docs(String description, Deprecation deprecation) {

    /** Documentation of an element that has neither a description nor a deprecation. */
    public static final Docs NONE = new Docs(null, null);

    /**
     * The deprecation of an element, from the {@code @deprecated} directive.
     *
     * @param reason the escaped reason; {@code No longer supported} when the directive gives none
     */
    public record Deprecation(String reason) {

        /**
         * Creates a deprecation.
         */
        public Deprecation {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
