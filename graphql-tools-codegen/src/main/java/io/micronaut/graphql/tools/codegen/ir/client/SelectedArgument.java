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
package io.micronaut.graphql.tools.codegen.ir.client;

import java.util.Objects;

/**
 * An argument of a selected field, as the document writes it.
 *
 * @param name  the argument name
 * @param value the value in GraphQL syntax: {@code 3}, {@code "text"} (quoted), {@code RED}, {@code [1, 2]}, {@code {a: 1}} or
 *              {@code $variable}
 */
public record SelectedArgument(String name, String value) {

    /**
     * Creates an argument.
     */
    public SelectedArgument {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
    }
}
