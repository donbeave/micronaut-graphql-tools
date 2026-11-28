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

import io.micronaut.graphql.tools.codegen.Origin;
import io.micronaut.graphql.tools.codegen.ir.shared.Presence;
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef;

import java.util.Objects;

/**
 * A variable of an operation.
 *
 * @param name         the name without the dollar sign; it is also the key in the request and the name of the component of the
 *                     variables type
 * @param type         the type of the variable, with the nullability of the declaration. An input object or an enum is a
 *                     reference to the generated type, and a custom scalar is mapped by {@code typeMappings}.
 * @param defaultValue the default value in GraphQL syntax, or {@code null} when the declaration has none
 * @param presence     what a left-out, a {@code null} and a provided value mean for this variable
 * @param origin       where the document declares the variable
 */
public record Variable(String name, TypeRef type, String defaultValue, Presence presence, Origin origin) {

    /**
     * Creates a variable.
     */
    public Variable {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(presence, "presence");
        Objects.requireNonNull(origin, "origin");
    }
}
