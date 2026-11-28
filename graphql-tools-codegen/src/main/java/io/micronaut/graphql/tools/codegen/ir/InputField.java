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
import io.micronaut.graphql.tools.codegen.ir.shared.AppliedDirective;
import io.micronaut.graphql.tools.codegen.ir.shared.Docs;
import io.micronaut.graphql.tools.codegen.ir.shared.Presence;
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef;

import java.util.List;
import java.util.Objects;

/**
 * A field of an input type.
 *
 * @param sdlName      the name in the schema
 * @param name         the name in the generated code; the SDL name until a renaming rule exists
 * @param type         the type of the field
 * @param defaultValue the default value in GraphQL syntax, or {@code null} when there is none
 * @param presence     what a left-out, a {@code null} and a provided value mean for this field: a nullable field tells an omitted
 *                     key from an explicit {@code null}, as the {@code ArgumentValue} of the server inputs does
 * @param docs         the description and the deprecation
 * @param directives   the applied directives, without {@code @deprecated}
 * @param origin       where the field is defined
 */
public record InputField(String sdlName, String name, TypeRef type, String defaultValue, Presence presence,
                         Docs docs, List<AppliedDirective> directives, Origin origin) {

    /**
     * Creates an input field, keeping an immutable copy of the list.
     */
    public InputField {
        Objects.requireNonNull(sdlName, "sdlName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(presence, "presence");
        Objects.requireNonNull(docs, "docs");
        Objects.requireNonNull(origin, "origin");
        directives = List.copyOf(directives);
    }
}
