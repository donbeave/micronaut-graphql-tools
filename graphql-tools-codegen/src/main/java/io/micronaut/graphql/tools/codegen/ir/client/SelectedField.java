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
import io.micronaut.graphql.tools.codegen.ir.shared.AppliedDirective;
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef;

import java.util.List;
import java.util.Objects;

/**
 * A field of a selection set, as the assembled document selects it. A field of an interface or union selection that the document
 * does not select as {@code __typename} appears here first, as the compile-time checks inject it.
 *
 * @param alias      the alias, or {@code null} when there is none; the response key is the alias or the field name
 * @param fieldName  the name of the field in the schema
 * @param arguments  the arguments in the order of the document
 * @param type       the type of the field in the schema, with its own nullability. A field with a selection set refers to the
 *                   schema type by name (a {@code Reference} whose names have the schema name, no package and the schema name as
 *                   the simple name), because its generated type belongs to the result models; a leaf field is a built-in, mapped
 *                   or enum type.
 * @param selection  the selection set of the field, or {@code null} for a leaf field
 * @param directives the directives of the field in the document, such as {@code @skip} and {@code @include}. A field with one of
 *                   them is absent from the response when it is skipped, even when its type is non-null; the result model
 *                   treats it as absent-able.
 * @param origin     where the document selects the field
 */
public record SelectedField(String alias, String fieldName, List<SelectedArgument> arguments, TypeRef type, SelectionSet selection,
                            List<AppliedDirective> directives, Origin origin) {

    /**
     * Creates a field, keeping immutable copies of the lists.
     */
    public SelectedField {
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(origin, "origin");
        arguments = List.copyOf(arguments);
        directives = List.copyOf(directives);
    }

    /**
     * The key of the field in the response.
     *
     * @return the alias, or the field name when there is no alias
     */
    public String responseKey() {
        return alias != null ? alias : fieldName;
    }
}
