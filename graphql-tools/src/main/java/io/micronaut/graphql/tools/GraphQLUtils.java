/*
 * Copyright 2021-2022 original authors
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
package io.micronaut.graphql.tools;

import graphql.language.EnumTypeDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.DirectiveInfo;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Alexey Zhokhov
 */
@Internal
final class GraphQLUtils {

    private GraphQLUtils() {
    }

    static String getType(TypeDefinition<?> typeDefinition) {
        return switch (typeDefinition) {
            case EnumTypeDefinition enumTypeDefinition -> "enum";
            case UnionTypeDefinition unionTypeDefinition -> "union";
            case ObjectTypeDefinition objectTypeDefinition -> "type";
            case InputObjectTypeDefinition inputObjectTypeDefinition -> "input";
            case InterfaceTypeDefinition interfaceTypeDefinition -> "interface";
            case ScalarTypeDefinition scalarTypeDefinition -> "scalar";
            case null, default -> throw unsupportedTypeDefinition(typeDefinition);
        };
    }

    /**
     * @param graphQlType a type reference
     * @return the type as written in the schema, e.g. {@code [String!]!}
     */
    static String printType(Type<?> graphQlType) {
        return switch (graphQlType) {
            case NonNullType nonNullType -> printType(nonNullType.getType()) + "!";
            case ListType listType -> "[" + printType(listType.getType()) + "]";
            case TypeName typeName -> typeName.getName();
            default -> graphQlType.toString();
        };
    }

    static TypeName getTypeName(Type<?> graphQlType) {
        return requireTypeName(unwrapNonNullType(graphQlType));
    }

    static Type<?> unwrapNonNullType(Type<?> type) {
        if (type instanceof NonNullType typeNonNull) {
            return unwrapNonNullType(typeNonNull.getType());
        }
        return type;
    }

    static TypeName requireTypeName(Type<?> graphQlType) {
        if (!(graphQlType instanceof TypeName)) {
            throw new UnsupportedOperationException("Unsupported type: " + graphQlType);
        }

        return (TypeName) graphQlType;
    }

    static UnsupportedOperationException unsupportedTypeDefinition(TypeDefinition<?> typeDefinition) {
        return new UnsupportedOperationException("Unsupported type definition " + typeDefinition);
    }

    /**
     * @param typeDefinitionRegistry the schema definitions
     * @return the enum, input object and scalar types that the arguments of the schema's own directive definitions
     * use, directly or through input object fields; graphql-java needs them for the directives, so they are reached
     */
    static Set<String> findDirectiveArgumentTypes(TypeDefinitionRegistry typeDefinitionRegistry) {
        Set<String> result = new HashSet<>();
        Deque<graphql.language.Type<?>> pending = new ArrayDeque<>();
        typeDefinitionRegistry.getDirectiveDefinitions().values().stream()
                .filter(it -> !DirectiveInfo.isGraphqlSpecifiedDirective(it.getName()))
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
