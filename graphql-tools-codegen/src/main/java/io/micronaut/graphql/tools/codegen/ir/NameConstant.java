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

import io.micronaut.graphql.tools.codegen.Origin;

import java.util.Objects;

/**
 * A constant of a name class: the identifier of the generated constant and the schema name that it holds.
 *
 * @param name   the identifier of the constant, which the rules derive from the schema name
 * @param value  the schema name: a type name, or a field name
 * @param origin where the schema element is defined
 */
public record NameConstant(String name, String value, Origin origin) {

    /**
     * Creates a constant.
     */
    public NameConstant {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(origin, "origin");
    }
}
