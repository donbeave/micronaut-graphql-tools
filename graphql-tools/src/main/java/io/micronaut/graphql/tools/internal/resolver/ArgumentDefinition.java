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
package io.micronaut.graphql.tools.internal.resolver;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.graphql.tools.internal.input.ValueConverter;

import java.util.Optional;

/**
 * @author Alexey Zhokhov
 */
@Internal
public final class ArgumentDefinition {

    private static final String SOURCE_ARGUMENT = "* SRC *";
    private static final String DATA_FETCHING_ENVIRONMENT_ARGUMENT = "* DFE *";

    private static final String DATA_LOADER_ARGUMENT = "* LOADER *";

    private final String name;
    private final Argument<?> inputValueArgument;
    private final ValueConverter converter;
    private final int dataLoaderIndex;

    private ArgumentDefinition(String name, @Nullable Argument<?> inputValueArgument,
                               @Nullable ValueConverter converter, int dataLoaderIndex) {
        ArgumentUtils.requireNonNull("name", name);
        this.name = name;
        this.inputValueArgument = inputValueArgument;
        this.converter = converter;
        this.dataLoaderIndex = dataLoaderIndex;
    }

    static ArgumentDefinition ofSourceArgument() {
        return new ArgumentDefinition(SOURCE_ARGUMENT, null, null, -1);
    }

    static ArgumentDefinition ofDataFetchingEnvironmentArgument() {
        return new ArgumentDefinition(DATA_FETCHING_ENVIRONMENT_ARGUMENT, null, null, -1);
    }

    static ArgumentDefinition ofInputValueArgument(String name, Argument<?> inputValueArgument,
                                                   ValueConverter converter) {
        ArgumentUtils.requireNonNull("inputValueArgument", inputValueArgument);
        ArgumentUtils.requireNonNull("converter", converter);
        return new ArgumentDefinition(name, inputValueArgument, converter, -1);
    }

    /**
     * @param index the index of the loader in the table of batch loader beans
     * @return the definition of a {@code DataLoader} parameter
     */
    static ArgumentDefinition ofDataLoaderArgument(int index) {
        return new ArgumentDefinition(DATA_LOADER_ARGUMENT, null, null, index);
    }

    public boolean isDataLoaderArgument() {
        return name.equals(DATA_LOADER_ARGUMENT);
    }

    /**
     * @return the index of the loader in the table of batch loader beans, for a {@code DataLoader} parameter
     */
    public int getDataLoaderIndex() {
        return dataLoaderIndex;
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

    /**
     * @return the converter of the value into the declared type (of the wrapped type for an {@code ArgumentValue}),
     * built at startup; {@code null} for the source and the {@code DataFetchingEnvironment}
     */
    @Nullable
    public ValueConverter getConverter() {
        return converter;
    }

}
