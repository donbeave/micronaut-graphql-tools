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

import java.util.List;
import java.util.Objects;

/**
 * An enum type.
 *
 * @param names      the SDL name and the generated name
 * @param docs       the description and the deprecation
 * @param values     the values in schema order, the values of an extension after those of the base definition
 * @param directives the applied directives, without {@code @deprecated}
 * @param origin     where the type is defined
 */
public record EnumType(Names names, Docs docs, List<EnumValue> values, List<AppliedDirective> directives, Origin origin) {

    /**
     * Creates an enum type, keeping immutable copies of the lists.
     */
    public EnumType {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(docs, "docs");
        Objects.requireNonNull(origin, "origin");
        values = List.copyOf(values);
        directives = List.copyOf(directives);
    }
}
