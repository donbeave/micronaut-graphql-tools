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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;

import java.util.Optional;

/**
 * @author Alexey Zhokhov
 */
@Internal
public final class ArgumentDefinition {

    private static final String SOURCE_ARGUMENT = "* SRC *";
    private static final String DATA_FETCHING_ENVIRONMENT_ARGUMENT = "* DFE *";

    private final String name;
    private final Argument<?> inputValueArgument;

    private ArgumentDefinition(String name, @Nullable Argument<?> inputValueArgument) {
        ArgumentUtils.requireNonNull("name", name);
        this.name = name;
        this.inputValueArgument = inputValueArgument;
    }

    static ArgumentDefinition ofSourceArgument() {
        return new ArgumentDefinition(SOURCE_ARGUMENT, null);
    }

    static ArgumentDefinition ofDataFetchingEnvironmentArgument() {
        return new ArgumentDefinition(DATA_FETCHING_ENVIRONMENT_ARGUMENT, null);
    }

    static ArgumentDefinition ofInputValueArgument(String name, Argument<?> inputValueArgument) {
        ArgumentUtils.requireNonNull("inputValueArgument", inputValueArgument);
        return new ArgumentDefinition(name, inputValueArgument);
    }

    public boolean isSourceArgument() {
        return name.equals(SOURCE_ARGUMENT);
    }

    public boolean isDataFetchingEnvironmentArgument() {
        return name.equals(DATA_FETCHING_ENVIRONMENT_ARGUMENT);
    }

    public String getName() {
        return name;
    }

    public Optional<Class<?>> getInputValueClass() {
        return getInputValueArgument().map(Argument::getType);
    }

    /**
     * @return the declared type of the method parameter, with its type arguments (e.g. {@code List<SomeInput>}), for
     * an argument of the GraphQL field; empty for the source and the {@code DataFetchingEnvironment}
     */
    public Optional<Argument<?>> getInputValueArgument() {
        return Optional.ofNullable(inputValueArgument);
    }

}
