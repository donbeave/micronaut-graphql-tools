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
package io.micronaut.graphql.tools.binding.internal.schema;

import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.Node;
import graphql.language.SchemaDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SourceLocation;
import graphql.language.TypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The result of the schema phase: the schema as the rules read it. It is immutable and shared by every later phase.
 *
 * @param registry the schema, with the type extensions merged
 * @param rootTypes the root operation types, keyed by operation ({@code query}, {@code mutation}, {@code subscription})
 */
@Internal
public record SchemaModel(TypeDefinitionRegistry registry, Map<String, String> rootTypes) {

    public SchemaModel {
        rootTypes = Collections.unmodifiableMap(new TreeMap<>(rootTypes));
    }

    /**
     * Reads the root operation types of a schema.
     *
     * @param registry the schema
     * @return the model, or empty when the schema has neither a {@code schema} definition nor an implicit root type
     */
    public static Optional<SchemaModel> load(TypeDefinitionRegistry registry) {
        Optional<SchemaDefinition> definition = registry.schemaDefinition();
        if (definition.isEmpty()) {
            Map<String, String> implicit = SchemaSources.implicitRoots(registry);
            return implicit.isEmpty() ? Optional.empty() : Optional.of(new SchemaModel(registry, implicit));
        }
        Map<String, String> roots = new TreeMap<>();
        for (OperationTypeDefinition operation : definition.get().getOperationTypeDefinitions()) {
            roots.putIfAbsent(operation.getName(), operation.getTypeName().getName());
        }
        return Optional.of(new SchemaModel(registry, roots));
    }

    /**
     * @return the names of the root operation types
     */
    public Set<String> rootTypeNames() {
        return new TreeSet<>(rootTypes.values());
    }

    /**
     * @return the name of the subscription type, when the schema has one
     */
    public Optional<String> subscriptionType() {
        return Optional.ofNullable(rootTypes.get("subscription"));
    }

    /**
     * Finds where a coordinate is defined in the schema source.
     *
     * @param coordinate a type name, {@code Type.field}, {@code Input.field} or {@code Enum.VALUE}
     * @return the location, when the schema was parsed from a source
     */
    public Optional<SourceLocation> locationOf(String coordinate) {
        int dot = coordinate.indexOf('.');
        String typeName = dot < 0 ? coordinate : coordinate.substring(0, dot);
        TypeDefinition<?> type = registry.getType(typeName).orElse(null);
        if (type == null) {
            return Optional.empty();
        }
        if (dot < 0) {
            return Optional.ofNullable(type.getSourceLocation());
        }
        String member = coordinate.substring(dot + 1);
        Node<?> found = null;
        if (type instanceof ObjectTypeDefinition object) {
            found = find(object.getFieldDefinitions(), member);
        } else if (type instanceof InterfaceTypeDefinition iface) {
            found = find(iface.getFieldDefinitions(), member);
        } else if (type instanceof InputObjectTypeDefinition input) {
            for (InputValueDefinition value : input.getInputValueDefinitions()) {
                if (value.getName().equals(member)) {
                    found = value;
                }
            }
        } else if (type instanceof EnumTypeDefinition enumType) {
            for (EnumValueDefinition value : enumType.getEnumValueDefinitions()) {
                if (value.getName().equals(member)) {
                    found = value;
                }
            }
        }
        return Optional.ofNullable(found).map(Node::getSourceLocation);
    }

    private static Node<?> find(List<FieldDefinition> fields, String name) {
        for (FieldDefinition field : fields) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }
}
