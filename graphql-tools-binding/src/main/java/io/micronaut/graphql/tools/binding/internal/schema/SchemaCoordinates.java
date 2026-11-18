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

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules of an explicit {@code Type.field} schema coordinate that need the schema only: its syntax, its type and field,
 * and which resolver kind may bind the root operation types.
 */
@Internal
public final class SchemaCoordinates {

    private static final Pattern SYNTAX = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*\\.[_A-Za-z][_0-9A-Za-z]*");

    private SchemaCoordinates() {
    }

    /**
     * What a resolver class is, as far as a coordinate rule cares.
     */
    public enum ResolverKind {
        /** A class that binds fields of the root operation types. */
        ROOT,
        /** A class that binds fields of the other object and interface types. */
        TYPE
    }

    /**
     * @param coordinate a candidate coordinate
     * @return whether it has the form {@code Type.field}
     */
    public static boolean isWellFormed(String coordinate) {
        return SYNTAX.matcher(coordinate).matches();
    }

    /**
     * @param coordinate a well-formed coordinate
     * @return the type part
     */
    public static String typeName(String coordinate) {
        return coordinate.substring(0, coordinate.indexOf('.'));
    }

    /**
     * @param coordinate a well-formed coordinate
     * @return the field part
     */
    public static String fieldName(String coordinate) {
        return coordinate.substring(coordinate.indexOf('.') + 1);
    }

    /**
     * Checks a coordinate that a resolver method declares against the schema.
     *
     * @param registry           the schema
     * @param operationTypeNames the names of the root operation types
     * @param coordinate         the declared coordinate
     * @param methodName         the name of the method that declares it
     * @param kinds              the kinds of the resolver class (a class can be both)
     * @return the sentence that names the first violation, or empty when the coordinate is valid
     */
    public static Optional<String> check(TypeDefinitionRegistry registry, Set<String> operationTypeNames,
                                         String coordinate, String methodName, Set<ResolverKind> kinds) {
        if (!isWellFormed(coordinate)) {
            return Optional.of("the value is not a schema coordinate of the form Type.field.");
        }

        String typeName = typeName(coordinate);
        String fieldName = fieldName(coordinate);

        Optional<TypeDefinition> typeDefinition = registry.getType(typeName);
        if (typeDefinition.isEmpty()) {
            return Optional.of("the schema has no type " + typeName + ".");
        }
        List<FieldDefinition> fieldDefinitions;
        if (typeDefinition.get() instanceof ObjectTypeDefinition objectTypeDefinition) {
            fieldDefinitions = objectTypeDefinition.getFieldDefinitions();
        } else if (typeDefinition.get() instanceof InterfaceTypeDefinition interfaceTypeDefinition) {
            fieldDefinitions = interfaceTypeDefinition.getFieldDefinitions();
        } else {
            return Optional.of(typeName + " is neither an object type nor an interface.");
        }
        if (fieldDefinitions.stream().noneMatch(it -> it.getName().equals(fieldName))) {
            return Optional.of("the type " + typeName + " has no field " + fieldName + ".");
        }

        if (!fieldName.equals(methodName)) {
            return Optional.of("the field name " + fieldName + " differs from the method name " + methodName + ".");
        }

        boolean operationType = operationTypeNames.contains(typeName);
        if (kinds.contains(ResolverKind.ROOT) && !operationType) {
            return Optional.of("a @GraphQLRootResolver binds fields of the root operation types "
                    + operationTypeNames + " only, not of " + typeName + ".");
        }
        if (kinds.contains(ResolverKind.TYPE) && operationType) {
            return Optional.of("a @GraphQLTypeResolver cannot bind a field of the root operation type "
                    + typeName + ".");
        }

        return Optional.empty();
    }
}
