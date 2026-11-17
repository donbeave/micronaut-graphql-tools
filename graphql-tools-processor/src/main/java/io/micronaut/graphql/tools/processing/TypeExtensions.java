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
package io.micronaut.graphql.tools.processing;

import graphql.language.EnumTypeDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Folds the type extensions of a schema ({@code extend type}, {@code extend interface}, {@code extend union},
 * {@code extend enum}, {@code extend input}, {@code extend schema}) into the definitions they extend, as the library
 * does before it binds the schema: an extension field is a field like any other, checked and bound.
 */
final class TypeExtensions {

    private TypeExtensions() {
    }

    /**
     * @param registry the schema as parsed
     * @return a registry with the same definitions, each type with the fields, values, members and interfaces of its
     * extensions, and no extensions
     */
    static TypeDefinitionRegistry merge(TypeDefinitionRegistry registry) {
        TypeDefinitionRegistry result = new TypeDefinitionRegistry();
        registry.schemaDefinition().ifPresent(schema -> add(result, mergeSchema(schema, registry)));
        registry.getDirectiveDefinitions().values().forEach(it -> add(result, it));
        registry.scalars().values().stream()
                .filter(it -> !ScalarInfo.isGraphqlSpecifiedScalar(it.getName()))
                .forEach(it -> add(result, it));
        for (TypeDefinition<?> type : registry.types().values()) {
            add(result, mergeType(type, registry));
        }
        return result;
    }

    private static SchemaDefinition mergeSchema(SchemaDefinition schema, TypeDefinitionRegistry registry) {
        if (registry.getSchemaExtensionDefinitions().isEmpty()) {
            return schema;
        }
        return schema.transform(builder -> {
            List<graphql.language.OperationTypeDefinition> operations =
                    new ArrayList<>(schema.getOperationTypeDefinitions());
            registry.getSchemaExtensionDefinitions().forEach(it -> operations.addAll(it.getOperationTypeDefinitions()));
            builder.operationTypeDefinitions(operations);
        });
    }

    private static TypeDefinition<?> mergeType(TypeDefinition<?> type, TypeDefinitionRegistry registry) {
        if (type instanceof ObjectTypeDefinition object) {
            List<? extends ObjectTypeDefinition> extensions = extensions(registry.objectTypeExtensions(), object);
            return extensions.isEmpty() ? object : object.transform(builder -> builder
                    .fieldDefinitions(concat(object.getFieldDefinitions(), extensions,
                            ObjectTypeDefinition::getFieldDefinitions))
                    .implementz(concat(object.getImplements(), extensions, ObjectTypeDefinition::getImplements))
                    .directives(concat(object.getDirectives(), extensions, ObjectTypeDefinition::getDirectives)));
        }
        if (type instanceof InterfaceTypeDefinition iface) {
            List<? extends InterfaceTypeDefinition> extensions = extensions(registry.interfaceTypeExtensions(), iface);
            return extensions.isEmpty() ? iface : iface.transform(builder -> builder
                    .definitions(concat(iface.getFieldDefinitions(), extensions,
                            InterfaceTypeDefinition::getFieldDefinitions))
                    .implementz(concat(iface.getImplements(), extensions, InterfaceTypeDefinition::getImplements))
                    .directives(concat(iface.getDirectives(), extensions, InterfaceTypeDefinition::getDirectives)));
        }
        if (type instanceof UnionTypeDefinition union) {
            List<? extends UnionTypeDefinition> extensions = extensions(registry.unionTypeExtensions(), union);
            return extensions.isEmpty() ? union : union.transform(builder -> builder
                    .memberTypes(concat(union.getMemberTypes(), extensions, UnionTypeDefinition::getMemberTypes))
                    .directives(concat(union.getDirectives(), extensions, UnionTypeDefinition::getDirectives)));
        }
        if (type instanceof EnumTypeDefinition enumType) {
            List<? extends EnumTypeDefinition> extensions = extensions(registry.enumTypeExtensions(), enumType);
            return extensions.isEmpty() ? enumType : enumType.transform(builder -> builder
                    .enumValueDefinitions(concat(enumType.getEnumValueDefinitions(), extensions,
                            EnumTypeDefinition::getEnumValueDefinitions))
                    .directives(concat(enumType.getDirectives(), extensions, EnumTypeDefinition::getDirectives)));
        }
        if (type instanceof InputObjectTypeDefinition input) {
            List<? extends InputObjectTypeDefinition> extensions =
                    extensions(registry.inputObjectTypeExtensions(), input);
            return extensions.isEmpty() ? input : input.transform(builder -> builder
                    .inputValueDefinitions(concat(input.getInputValueDefinitions(), extensions,
                            InputObjectTypeDefinition::getInputValueDefinitions))
                    .directives(concat(input.getDirectives(), extensions, InputObjectTypeDefinition::getDirectives)));
        }
        return type;
    }

    private static <T extends TypeDefinition<?>> List<? extends T> extensions(
            Map<String, ? extends List<? extends T>> extensions, T type) {
        List<? extends T> result = extensions.get(type.getName());
        return result == null ? List.of() : result;
    }

    private static <T, E> List<T> concat(List<T> own, List<? extends E> extensions,
                                         Function<E, List<T>> part) {
        List<T> result = new ArrayList<>(own);
        extensions.forEach(it -> result.addAll(part.apply(it)));
        return result;
    }

    private static void add(TypeDefinitionRegistry registry, SDLDefinition<?> definition) {
        registry.add(definition).ifPresent(error -> {
            throw new IllegalStateException("Cannot fold the type extensions of the schema: " + error.getMessage());
        });
    }

}
