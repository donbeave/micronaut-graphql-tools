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
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.graphql.tools.ArgumentDefinition;
import io.micronaut.graphql.tools.ArgumentValue;

import java.util.List;

/**
 * @author Alexey Zhokhov
 */
@Internal
public final class MicronautExecutableMethodDataFetcher implements DataFetcher<Object> {

    private static final Object[] NO_ARGUMENTS = new Object[0];

    private static final int SOURCE = 0;
    private static final int DATA_FETCHING_ENVIRONMENT = 1;
    private static final int ARGUMENT_VALUE = 2;
    private static final int INPUT_VALUE = 3;

    private final InputValueConverter inputValueConverter;
    private final Executable<Object, ?> executable;
    private final int[] kinds;
    private final String[] names;
    private final JavaType[] javaTypes;
    private final Object instance;

    public MicronautExecutableMethodDataFetcher(
            InputValueConverter inputValueConverter,
            Executable<Object, ?> executable,
            List<ArgumentDefinition> argumentDefinitions,
            @Nullable Object instance
    ) {
        this.inputValueConverter = inputValueConverter;
        this.executable = executable;
        int size = argumentDefinitions.size();
        this.kinds = new int[size];
        this.names = new String[size];
        this.javaTypes = new JavaType[size];
        for (int i = 0; i < size; i++) {
            ArgumentDefinition argumentDefinition = argumentDefinitions.get(i);
            names[i] = argumentDefinition.getName();
            if (argumentDefinition.isSourceArgument()) {
                kinds[i] = SOURCE;
            } else if (argumentDefinition.isDataFetchingEnvironmentArgument()) {
                kinds[i] = DATA_FETCHING_ENVIRONMENT;
            } else {
                Argument<?> argument = argumentDefinition.getInputValueArgument().orElseThrow();
                boolean argumentValue = ArgumentValue.class.equals(argument.getType());
                kinds[i] = argumentValue ? ARGUMENT_VALUE : INPUT_VALUE;
                javaTypes[i] = inputValueConverter.toJavaType(
                        argumentValue ? argument.getFirstTypeVariable().orElseThrow() : argument);
            }
        }
        this.instance = instance;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        int size = kinds.length;
        Object[] arguments = size == 0 ? NO_ARGUMENTS : new Object[size];

        for (int i = 0; i < size; i++) {
            arguments[i] = argument(environment, i);
        }

        return executable.invoke(instance != null ? instance : environment.getSource(), arguments);
    }

    private Object argument(DataFetchingEnvironment environment, int index) {
        return switch (kinds[index]) {
            case SOURCE -> environment.getSource();
            case DATA_FETCHING_ENVIRONMENT -> environment;
            case ARGUMENT_VALUE -> {
                // graphql-java leaves an argument out of the map only if the client did not provide it and the
                // schema declares no default value for it
                if (environment.containsArgument(names[index])) {
                    Object argumentValue = environment.getArgument(names[index]);
                    yield ArgumentValue.ofNullable(inputValueConverter.convert(argumentValue, javaTypes[index]));
                }
                yield ArgumentValue.omitted();
            }
            default -> inputValueConverter.convert(environment.getArgument(names[index]), javaTypes[index]);
        };
    }

}
