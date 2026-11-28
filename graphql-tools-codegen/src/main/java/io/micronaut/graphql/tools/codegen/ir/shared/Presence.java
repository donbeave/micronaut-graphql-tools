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

/**
 * What the value of an input position tells apart: a variable of an operation or a field of an input object. It is a fact of the
 * GraphQL input rules, so it holds for every mode; a generated type decides how to show it. An argument that a variable fills is
 * left out when the variable is not provided, and a left-out value is not an explicit {@code null}.
 */
public enum Presence {
    /** The key is always sent and is never {@code null}: a non-null type without a default value. */
    REQUIRED,
    /** The key may be left out and is never {@code null}: a non-null type with a default value, which the server applies. */
    OMITTABLE,
    /** A left-out key, an explicit {@code null} and a value mean three things: a nullable type, with or without a default value. */
    TRI_STATE;

    /**
     * The presence of an input position.
     *
     * @param type        the type of the position, with its nullability
     * @param hasDefault  whether the position has a default value
     * @return the presence
     */
    public static Presence of(TypeRef type, boolean hasDefault) {
        if (type.isNullable()) {
            return TRI_STATE;
        }
        return hasDefault ? OMITTABLE : REQUIRED;
    }
}
