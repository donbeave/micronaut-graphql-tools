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
package io.micronaut.graphql.tools.codegen.schema;

import graphql.language.Directive;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.Node;
import graphql.language.TypeDefinition;
import io.micronaut.graphql.tools.codegen.Origin;
import io.micronaut.graphql.tools.codegen.ir.shared.Operation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The schema files merged into one definition for each type name, in the order of the definitions: the output of the loader and the
 * input of the rules. It is the only place that holds graphql-java nodes.
 */
final class MergedSchema {

    final Map<String, MergedType> types = new LinkedHashMap<>();
    final Map<Operation, Root> roots = new EnumMap<>(Operation.class);

    record Root(String typeName, Origin origin) {
    }

    /**
     * One type with the content of its base definition and of all its extensions.
     */
    static final class MergedType {

        enum Kind { OBJECT, INTERFACE, UNION, ENUM, INPUT, SCALAR }

        final String name;
        final Kind kind;
        final TypeDefinition<?> base;
        final Origin origin;
        final List<FieldDefinition> fields = new ArrayList<>();
        final List<InputValueDefinition> inputFields = new ArrayList<>();
        final List<EnumValueDefinition> values = new ArrayList<>();
        final Set<String> implemented = new LinkedHashSet<>();
        final Set<String> members = new LinkedHashSet<>();
        final List<Directive> directives = new ArrayList<>();
        final Map<String, Node<?>> declared = new HashMap<>();

        MergedType(String name, Kind kind, TypeDefinition<?> base, Origin origin) {
            this.name = name;
            this.kind = kind;
            this.base = base;
            this.origin = origin;
        }
    }
}
