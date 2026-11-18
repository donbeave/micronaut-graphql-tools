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

import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.language.TypeName;
import io.micronaut.core.annotation.Internal;

/**
 * How the rules print a GraphQL type: {@code [Book!]!}.
 */
@Internal
public final class SchemaTypes {

    private SchemaTypes() {
    }

    /**
     * @param graphQlType a type of the schema
     * @return the type as the schema language writes it
     */
    public static String print(Type<?> graphQlType) {
        if (graphQlType instanceof NonNullType nonNullType) {
            return print(nonNullType.getType()) + "!";
        } else if (graphQlType instanceof ListType listType) {
            return "[" + print(listType.getType()) + "]";
        } else if (graphQlType instanceof TypeName typeName) {
            return typeName.getName();
        }
        return graphQlType.toString();
    }
}
