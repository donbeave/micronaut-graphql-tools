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
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.SlotKind;
import io.micronaut.graphql.tools.internal.input.ValueConverter;

import java.security.Principal;
import java.util.Optional;

/**
 * How one parameter of a resolver method is bound: the kind of the slot in the argument plan of the field and what the
 * kind needs, all decided at startup.
 *
 * @author Alexey Zhokhov
 */
@Internal
public final class ArgumentDefinition {

    private final SlotKind kind;
    private final String name;
    private final Argument<?> inputValueArgument;
    private final ValueConverter converter;
    private final int dataLoaderIndex;
    private final Class<? extends Principal> principalType;

    private ArgumentDefinition(SlotKind kind, String name, @Nullable Argument<?> inputValueArgument,
                               @Nullable ValueConverter converter, int dataLoaderIndex, @Nullable Class<? extends Principal> principalType) {
        ArgumentUtils.requireNonNull("name", name);
        this.kind = kind;
        this.name = name;
        this.inputValueArgument = inputValueArgument;
        this.converter = converter;
        this.dataLoaderIndex = dataLoaderIndex;
        this.principalType = principalType;
    }

    static ArgumentDefinition ofSourceArgument() {
        return ofKind(SlotKind.SOURCE);
    }

    static ArgumentDefinition ofDataFetchingEnvironmentArgument() {
        return ofKind(SlotKind.ENVIRONMENT);
    }

    /**
     * @param kind a kind that needs nothing more than the kind itself
     * @return the definition of a parameter of that kind
     */
    static ArgumentDefinition ofKind(SlotKind kind) {
        return new ArgumentDefinition(kind, "* " + kind + " *", null, null, -1, null);
    }

    static ArgumentDefinition ofInputValueArgument(String name, Argument<?> inputValueArgument,
                                                   ValueConverter converter) {
        ArgumentUtils.requireNonNull("inputValueArgument", inputValueArgument);
        ArgumentUtils.requireNonNull("converter", converter);
        return new ArgumentDefinition(SlotKind.ARGUMENT, name, inputValueArgument, converter, -1, null);
    }

    /**
     * @param key the key in the {@code GraphQLContext}
     * @return the definition of a context value parameter
     */
    static ArgumentDefinition ofContextValue(String key) {
        return new ArgumentDefinition(SlotKind.CONTEXT_VALUE, key, null, null, -1, null);
    }

    /**
     * @param type the declared type of the parameter, a {@code Principal} or a subtype
     * @return the definition of a principal parameter
     */
    static ArgumentDefinition ofPrincipal(Class<? extends Principal> type) {
        return new ArgumentDefinition(SlotKind.PRINCIPAL, "* PRINCIPAL *", null, null, -1, type);
    }

    /**
     * @param index the index of the loader in the table of batch loader beans
     * @return the definition of a {@code DataLoader} parameter
     */
    static ArgumentDefinition ofDataLoaderArgument(int index) {
        return new ArgumentDefinition(SlotKind.DATA_LOADER, "* LOADER *", null, null, index, null);
    }

    /**
     * @return what the parameter binds
     */
    public SlotKind getKind() {
        return kind;
    }

    public boolean isDataLoaderArgument() {
        return kind == SlotKind.DATA_LOADER;
    }

    /**
     * @return the index of the loader in the table of batch loader beans, for a {@code DataLoader} parameter
     */
    public int getDataLoaderIndex() {
        return dataLoaderIndex;
    }

    /**
     * @return the declared type of a principal parameter, else {@code null}
     */
    @Nullable
    public Class<? extends Principal> getPrincipalType() {
        return principalType;
    }

    public boolean isSourceArgument() {
        return kind == SlotKind.SOURCE;
    }

    public boolean isDataFetchingEnvironmentArgument() {
        return kind == SlotKind.ENVIRONMENT;
    }

    /**
     * @return the argument name, the context key for a context value, else a placeholder
     */
    public String getName() {
        return name;
    }

    public Optional<Class<?>> getInputValueClass() {
        return getInputValueArgument().map(Argument::getType);
    }

    /**
     * @return the declared type of the method parameter, with its type arguments (e.g. {@code List<SomeInput>}), for
     * an argument of the GraphQL field; empty for every other kind
     */
    public Optional<Argument<?>> getInputValueArgument() {
        return Optional.ofNullable(inputValueArgument);
    }

    /**
     * @return the converter of the value into the declared type (of the wrapped type for an {@code ArgumentValue}),
     * built at startup; {@code null} for every kind but an argument of the field
     */
    @Nullable
    public ValueConverter getConverter() {
        return converter;
    }

}
