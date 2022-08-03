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

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Executable;
import io.micronaut.graphql.tools.ArgumentDefinition;

import java.util.ArrayList;
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
        this.instance = instance;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        List<Object> arguments = new ArrayList<>();

        for (ArgumentDefinition argumentDefinition : argumentDefinitions) {
            if (argumentDefinition.isSourceArgument()) {
                arguments.add(environment.getSource());
            } else if (argumentDefinition.isDataFetchingEnvironmentArgument()) {
                arguments.add(environment);
            } else {
                Object argumentValue = environment.getArgument(argumentDefinition.getName());

                if (argumentValue != null) {
                    Class<?> inputValueClass = argumentDefinition.getInputValueClass().get();

                    if (argumentValue.getClass().isAssignableFrom(inputValueClass)) {
                        arguments.add(argumentValue);
                    } else {
                        Object convertedValue = objectMapper.convertValue(argumentValue, inputValueClass);

                        arguments.add(convertedValue);
                    }
                } else {
                    arguments.add(null);
                }
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
