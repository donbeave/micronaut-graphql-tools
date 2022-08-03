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

import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ObjectTypeDefinition;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.Optional;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;

/**
 * @author Alexey Zhokhov
 */
public final class TypeMappingContext implements MappingContext {

    private final ObjectTypeDefinition objectTypeDefinition;
    private final String fieldName;
    private final String argumentName;

    private final Class<?> mappedClass;
    private final String mappedMethod;

    private TypeMappingContext(ObjectTypeDefinition objectTypeDefinition, String fieldName,
                               @Nullable String argumentName, @Nullable Class<?> mappedClass,
                               @Nullable String mappedMethod) {
        this.objectTypeDefinition = objectTypeDefinition;
        this.fieldName = fieldName;
        this.argumentName = argumentName;
        this.mappedClass = mappedClass;
        this.mappedMethod = mappedMethod;
    }

    static TypeMappingContext forField(@NonNull ObjectTypeDefinition objectTypeDefinition, @NonNull String fieldName) {
        requireNonNull("objectTypeDefinition", objectTypeDefinition);
        requireNonNull("fieldName", fieldName);

        return new TypeMappingContext(objectTypeDefinition, fieldName, null, null,
                null);
    }

    static TypeMappingContext forField(@NonNull TypeMappingContext mappingContext, @NonNull Class<?> mappedClass,
                                       @Nullable String mappedMethod) {
        requireNonNull("mappingContext", mappingContext);
        requireNonNull("mappedClass", mappedClass);

        return new TypeMappingContext(mappingContext.objectTypeDefinition, mappingContext.fieldName,
                null, mappedClass, mappedMethod);
    }

    static TypeMappingContext forArgument(@NonNull TypeMappingContext mappingContext, @NonNull String argumentName) {
        requireNonNull("mappingContext", mappingContext);
        requireNonNull("mappingContext.objectTypeDefinition", mappingContext.objectTypeDefinition);
        requireNonNull("mappingContext.fieldName", mappingContext.fieldName);
        requireNonNull("argumentName", argumentName);

        return new TypeMappingContext(
                mappingContext.objectTypeDefinition,
                mappingContext.fieldName,
                argumentName,
                mappingContext.getMappedClass(),
                mappingContext.getMappedMethod()
        );
    }

    public ObjectTypeDefinition getObjectTypeDefinition() {
        return objectTypeDefinition;
    }

    public FieldDefinition getFieldDefinition() {
        return objectTypeDefinition.getFieldDefinitions().stream()
                .filter(it -> it.getName().equals(fieldName))
                .findFirst()
                .get();
    }

    public Optional<InputValueDefinition> getInputValueDefinition() {
        if (argumentName == null) {
            return Optional.empty();
        }

        return getFieldDefinition().getInputValueDefinitions().stream()
                .filter(it -> it.getName().equals(argumentName))
                .findFirst();
    }

    public String getGraphQlObjectType() {
        return objectTypeDefinition.getName();
    }

    @Nullable
    public String getGraphQlField() {
        return fieldName;
    }

    @Nullable
    public String getGraphQlArgument() {
        return argumentName;
    }

    @Override
    @Nullable
    public Class<?> getMappedClass() {
        return mappedClass;
    }

    @Nullable
    public String getMappedMethod() {
        return mappedMethod;
    }

    @Override
    public String getMessage(String superMessage) {
        StringBuilder builder = new StringBuilder(superMessage);

        builder.append("\n  GraphQL object type: ").append(getGraphQlObjectType());
        builder.append("\n  GraphQL field: ").append(getGraphQlField());

        if (argumentName != null) {
            builder.append("\n  GraphQL argument: ").append(argumentName);
        }

        if (mappedClass != null) {
            builder.append("\n  Mapped class: ").append(mappedClass.getName());
        }
        if (mappedMethod != null) {
            builder.append("\n  Mapped method: ").append(mappedMethod);
        }

        return builder.toString();
    }

}
