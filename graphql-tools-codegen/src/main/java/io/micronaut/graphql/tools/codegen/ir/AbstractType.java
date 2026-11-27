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
 * An interface type, a union type, or an object type that the {@code interfaceTypes} option selects: each is generated as a Java
 * interface.
 *
 * @param kind        what the schema defines
 * @param names       the SDL name and the generated name
 * @param docs        the description and the deprecation
 * @param fields      the fields in schema order; empty for a union
 * @param implemented the interfaces that this type implements (an interface that implements another keeps it, so the
 *                    emitter writes an extending interface and the assignability between the interfaces is preserved)
 * @param members     the generated types that belong to this one, in schema order: the member types of a union, the object
 *                    types and the interfaces that implement an interface; empty for a selected object type
 * @param sealed      whether the interface is sealed and permits exactly {@code members}. It is {@code false} for a selected
 *                    object type, for a type without members, and for a type that has a member that the {@code typeMappings}
 *                    option maps to an external class, because an external class cannot be listed.
 * @param directives  the applied directives, without {@code @deprecated}
 * @param origin      where the type is defined
 */
public record AbstractType(Kind kind, Names names, Docs docs, List<Field> fields, List<Names> implemented, List<Names> members,
                           boolean sealed, List<AppliedDirective> directives, Origin origin) {

    /**
     * Creates an abstract type, keeping immutable copies of the lists.
     */
    public AbstractType {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(docs, "docs");
        Objects.requireNonNull(origin, "origin");
        fields = List.copyOf(fields);
        implemented = List.copyOf(implemented);
        members = List.copyOf(members);
        directives = List.copyOf(directives);
    }

    /**
     * What the schema defines.
     */
    public enum Kind {
        /** An interface type. */
        INTERFACE,
        /** A union type. */
        UNION,
        /** An object type that the {@code interfaceTypes} option selects. */
        OBJECT
    }
}
