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
package io.micronaut.graphql.tools.internal;

import graphql.language.EnumTypeDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaTypes;
import io.micronaut.graphql.tools.internal.wiring.GraphQLSpecifiedDirectives;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** Helpers over graphql-java schema types that the binding classes share. */
@Internal
public final class GraphQLUtils {

    private GraphQLUtils() {
    }

    public static String getType(TypeDefinition<?> typeDefinition) {
        if (typeDefinition instanceof EnumTypeDefinition) {
            return "enum";
        } else if (typeDefinition instanceof UnionTypeDefinition) {
            return "union";
        } else if (typeDefinition instanceof ObjectTypeDefinition) {
            return "type";
        } else if (typeDefinition instanceof InputObjectTypeDefinition) {
            return "input";
        } else if (typeDefinition instanceof InterfaceTypeDefinition) {
            return "interface";
        } else if (typeDefinition instanceof ScalarTypeDefinition) {
            return "scalar";
        }
        throw unsupportedTypeDefinition(typeDefinition);
    }

    public static String printType(Type<?> graphQlType) {
        return SchemaTypes.print(graphQlType);
    }

    public static TypeName getTypeName(Type<?> graphQlType) {
        return requireTypeName(unwrapNonNullType(graphQlType));
    }

    public static Type<?> unwrapNonNullType(Type<?> type) {
        if (type instanceof NonNullType typeNonNull) {
            return unwrapNonNullType(typeNonNull.getType());
        }
        return type;
    }

    public static TypeName requireTypeName(Type<?> graphQlType) {
        if (!(graphQlType instanceof TypeName)) {
            throw new UnsupportedOperationException("Unsupported type: " + graphQlType);
        }

        return (TypeName) graphQlType;
    }

    public static UnsupportedOperationException unsupportedTypeDefinition(TypeDefinition<?> typeDefinition) {
        return new UnsupportedOperationException("Unsupported type definition " + typeDefinition);
    }

    public static Set<String> findDirectiveArgumentTypes(TypeDefinitionRegistry typeDefinitionRegistry) {
        Set<String> result = new HashSet<>();
        Deque<graphql.language.Type<?>> pending = new ArrayDeque<>();
        typeDefinitionRegistry.getDirectiveDefinitions().values().stream()
                .filter(it -> !GraphQLSpecifiedDirectives.isSpecified(it.getName()))
                .forEach(it -> it.getInputValueDefinitions().forEach(argument -> pending.add(argument.getType())));
        while (!pending.isEmpty()) {
            String name = getTypeName(pending.poll()).getName();
            if (result.add(name)) {
                typeDefinitionRegistry.getType(name, InputObjectTypeDefinition.class).ifPresent(input ->
                        input.getInputValueDefinitions().forEach(field -> pending.add(field.getType())));
            }
        }
        return result;
    }

}
