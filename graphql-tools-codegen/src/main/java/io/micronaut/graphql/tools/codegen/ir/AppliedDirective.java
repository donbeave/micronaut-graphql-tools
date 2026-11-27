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
package io.micronaut.graphql.tools.codegen.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A directive applied to a schema element, kept as its name and its arguments written as strings, because the selector
 * options of the generator name directives. The {@code @deprecated} directive is not listed: it is in {@link Docs}.
 *
 * @param name      the directive name, without the {@code @}
 * @param arguments the arguments in schema order. A string value is its content, any other value is written in GraphQL syntax
 *                  ({@code 3}, {@code [1, 2]}, {@code {a: 1}}, {@code RED}).
 */
public record AppliedDirective(String name, Map<String, String> arguments) {

    /**
     * Creates a directive, keeping an immutable copy of the arguments in their order.
     */
    public AppliedDirective {
        Objects.requireNonNull(name, "name");
        arguments = arguments.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
