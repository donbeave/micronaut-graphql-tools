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
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import io.micronaut.core.annotation.Internal;

/**
 * @author Alexey Zhokhov
 */
@Internal
final class GraphQLUtils {

    private GraphQLUtils() {
    }

    static String getType(TypeDefinition<?> typeDefinition) {
        if (typeDefinition instanceof EnumTypeDefinition) {
            return "enum";
        } else if (typeDefinition instanceof UnionTypeDefinition) {
            return "union";
        } else if (typeDefinition instanceof ObjectTypeDefinition) {
            return "type";
        } else if (typeDefinition instanceof InputObjectTypeDefinition) {
            return "input";
        } else {
            throw unsupportedTypeDefinition(typeDefinition);
        }
    }

    static TypeName getTypeName(Type<?> graphQlType) {
        return requireTypeName(unwrapNonNullType(graphQlType));
    }

    static Type<?> unwrapNonNullType(Type<?> type) {
        if (type instanceof NonNullType) {
            return unwrapNonNullType(((NonNullType) type).getType());
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

}
