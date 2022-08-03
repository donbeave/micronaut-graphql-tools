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

import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;

/**
 * @author Alexey Zhokhov
 */
public final class InputMappingContext implements MappingContext {

    private final InputObjectTypeDefinition inputObjectTypeDefinition;
    private final String inputValueName;

    private final Class<?> mappedClass;
    private final String mappedProperty;

    public InputMappingContext(@NonNull InputObjectTypeDefinition inputObjectTypeDefinition,
                               @NonNull String inputValueName,
                               @NonNull Class<?> mappedClass,
                               @Nullable String mappedProperty) {
        requireNonNull("inputObjectTypeDefinition", inputObjectTypeDefinition);
        requireNonNull("inputValueName", inputValueName);
        requireNonNull("mappedClass", mappedClass);

        this.inputObjectTypeDefinition = inputObjectTypeDefinition;
        this.inputValueName = inputValueName;
        this.mappedClass = mappedClass;
        this.mappedProperty = mappedProperty;
    }

    @Override
    public Class<?> getMappedClass() {
        return mappedClass;
    }

    @Nullable
    public String getMappedProperty() {
        return mappedProperty;
    }

    @Override
    public String getMessage(String superMessage) {
        StringBuilder builder = new StringBuilder(superMessage);

        builder.append("\n  GraphQL input object type: ").append(getGraphQlInputObjectType());

        if (inputValueName != null) {
            builder.append("\n  GraphQL input value: ").append(inputValueName);
        }

        if (mappedClass != null) {
            builder.append("\n  Mapped class: ").append(mappedClass.getName());
        }

        if (mappedProperty != null) {
            builder.append("\n  Mapped property: ").append(mappedProperty);
        }

        return builder.toString();
    }

    public InputObjectTypeDefinition getInputObjectTypeDefinition() {
        return inputObjectTypeDefinition;
    }

    public InputValueDefinition getInputValueDefinition() {
        return inputObjectTypeDefinition.getInputValueDefinitions().stream()
                .filter(it -> it.getName().equals(inputValueName))
                .findFirst()
                .get();
    }

    public String getGraphQlInputObjectType() {
        return inputObjectTypeDefinition.getName();
    }

    public String getGraphQlInputValue() {
        return inputValueName;
    }

}
