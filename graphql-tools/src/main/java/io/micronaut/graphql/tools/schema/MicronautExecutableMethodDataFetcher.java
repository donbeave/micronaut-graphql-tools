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
package io.micronaut.graphql.tools.schema;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.graphql.tools.ArgumentDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Alexey Zhokhov
 */
@Internal
public final class MicronautExecutableMethodDataFetcher implements DataFetcher<Object> {

    private final ObjectMapper objectMapper;
    private final Executable<Object, ?> executable;
    private final List<ArgumentDefinition> argumentDefinitions;
    private final JavaType[] javaTypes;
    private final Object instance;

    public MicronautExecutableMethodDataFetcher(
            ObjectMapper objectMapper,
            Executable<Object, ?> executable,
            List<ArgumentDefinition> argumentDefinitions,
            @Nullable Object instance
    ) {
        this.objectMapper = objectMapper;
        this.executable = executable;
        this.argumentDefinitions = new CopyOnWriteArrayList<>(argumentDefinitions);
        this.javaTypes = argumentDefinitions.stream()
                .map(it -> it.getInputValueArgument()
                        .map(argument -> toJavaType(objectMapper.getTypeFactory(), argument))
                        .orElse(null))
                .toArray(JavaType[]::new);
        this.instance = instance;
    }

    /**
     * @param typeFactory the type factory of the mapper
     * @param argument    a declared Java type with its type arguments, e.g. {@code List<List<SomeInput>>}
     * @return the Jackson type with the same type arguments
     */
    static JavaType toJavaType(TypeFactory typeFactory, Argument<?> argument) {
        Argument<?>[] typeParameters = argument.getTypeParameters();
        if (typeParameters.length == 0) {
            return typeFactory.constructType(argument.getType());
        }
        JavaType[] parameterTypes = Arrays.stream(typeParameters)
                .map(it -> toJavaType(typeFactory, it))
                .toArray(JavaType[]::new);
        return typeFactory.constructParametricType(argument.getType(), parameterTypes);
    }

    /**
     * Converts a value graphql-java coerced (a scalar value, an enum name, a {@code Map} for an input object or a
     * {@code List} of these) into the declared Java type, including its type arguments. A value that already is an
     * instance of a declared type without type arguments is passed as is.
     */
    @Nullable
    private Object convert(@Nullable Object value, JavaType javaType) {
        if (value == null) {
            return null;
        }
        if (!javaType.hasGenericTypes() && javaType.getRawClass().isInstance(value)) {
            return value;
        }
        return objectMapper.convertValue(value, javaType);
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        List<Object> arguments = new ArrayList<>();

        for (int i = 0; i < argumentDefinitions.size(); i++) {
            ArgumentDefinition argumentDefinition = argumentDefinitions.get(i);
            if (argumentDefinition.isSourceArgument()) {
                arguments.add(environment.getSource());
            } else if (argumentDefinition.isDataFetchingEnvironmentArgument()) {
                arguments.add(environment);
            } else {
                Object argumentValue = environment.getArgument(argumentDefinition.getName());

                arguments.add(convert(argumentValue, javaTypes[i]));
            }
        }

        if (instance != null) {
            // execute root query
            return executable.invoke(instance, arguments.toArray());
        } else {
            return executable.invoke(environment.getSource(), arguments.toArray());
        }
    }

}
