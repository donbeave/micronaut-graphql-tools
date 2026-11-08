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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Alexey Zhokhov
 */
@Internal
public final class MicronautExecutableMethodDataFetcher implements DataFetcher<Object> {

    private final InputValueConverter inputValueConverter;
    private final Executable<Object, ?> executable;
    private final List<ArgumentDefinition> argumentDefinitions;
    private final JavaType[] javaTypes;
    private final boolean[] argumentValues;
    private final Object instance;

    public MicronautExecutableMethodDataFetcher(
            InputValueConverter inputValueConverter,
            Executable<Object, ?> executable,
            List<ArgumentDefinition> argumentDefinitions,
            @Nullable Object instance
    ) {
        this.inputValueConverter = inputValueConverter;
        this.executable = executable;
        this.argumentDefinitions = new CopyOnWriteArrayList<>(argumentDefinitions);
        this.javaTypes = new JavaType[argumentDefinitions.size()];
        this.argumentValues = new boolean[argumentDefinitions.size()];
        for (int i = 0; i < argumentDefinitions.size(); i++) {
            Argument<?> argument = argumentDefinitions.get(i).getInputValueArgument().orElse(null);
            if (argument != null) {
                // the binding allows ArgumentValue only with a declared type argument
                argumentValues[i] = ArgumentValue.class.equals(argument.getType());
                javaTypes[i] = inputValueConverter.toJavaType(
                        argumentValues[i] ? argument.getFirstTypeVariable().orElseThrow() : argument);
            }
        }
        this.instance = instance;
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
            } else if (argumentValues[i]) {
                // graphql-java leaves an argument out of the map only if the client did not provide it and the
                // schema declares no default value for it
                if (environment.containsArgument(argumentDefinition.getName())) {
                    Object argumentValue = environment.getArgument(argumentDefinition.getName());
                    arguments.add(ArgumentValue.ofNullable(inputValueConverter.convert(argumentValue, javaTypes[i])));
                } else {
                    arguments.add(ArgumentValue.omitted());
                }
            } else {
                Object argumentValue = environment.getArgument(argumentDefinition.getName());

                arguments.add(inputValueConverter.convert(argumentValue, javaTypes[i]));
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
