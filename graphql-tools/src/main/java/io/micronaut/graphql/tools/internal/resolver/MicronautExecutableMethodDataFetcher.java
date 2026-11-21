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

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLNamedType;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.SlotKind;
import io.micronaut.http.HttpRequest;
import io.micronaut.graphql.tools.input.ArgumentValue;
import io.micronaut.graphql.tools.internal.execution.ExecutionState;
import io.micronaut.graphql.tools.internal.execution.LazyDataLoaderRegistry;
import io.micronaut.graphql.tools.internal.execution.SuspendInvoker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.micronaut.graphql.tools.internal.input.InputConversionException;
import io.micronaut.graphql.tools.internal.input.ValueConverter;
import io.micronaut.graphql.tools.internal.input.ValueConverters;

/**
 * @author Alexey Zhokhov
 */
@Internal
public final class MicronautExecutableMethodDataFetcher implements DataFetcher<Object> {

    /**
     * The {@code extensions.code} of the error that a value which does not convert becomes.
     */
    public static final String INPUT_CONVERSION_FAILED = "INPUT_CONVERSION_FAILED";

    private static final Logger LOG = LoggerFactory.getLogger(MicronautExecutableMethodDataFetcher.class);

    private static final Object[] NO_ARGUMENTS = new Object[0];

    private static final int SOURCE = 0;
    private static final int DATA_FETCHING_ENVIRONMENT = 1;
    private static final int ARGUMENT_VALUE = 2;
    private static final int INPUT_VALUE = 3;
    private static final int DATA_LOADER = 4;
    private static final int CONTEXT = 5;
    private static final int CONTEXT_VALUE = 6;
    private static final int SELECTION_SET = 7;
    private static final int LOCALE = 8;
    private static final int PRINCIPAL = 9;
    private static final int REQUEST = 10;
    private static final int RESPONSE = 11;
    private static final int OPTIONAL_RESPONSE = 12;

    private final Executable<Object, ?> executable;
    private final int[] kinds;
    private final String[] names;
    private final ValueConverter[] converters;
    private final int[] loaders;
    private final Class<? extends Principal>[] principalTypes;
    private final boolean needsState;
    private final Object instance;
    private final PerCallResolver perCall;
    @Nullable
    private final SuspendInvoker suspendInvoker;

    public MicronautExecutableMethodDataFetcher(
            Executable<Object, ?> executable,
            List<ArgumentDefinition> argumentDefinitions,
            @Nullable Object instance
    ) {
        this(executable, argumentDefinitions, instance, null);
    }

    /**
     * @param executable          the method
     * @param argumentDefinitions how each parameter of the method is bound
     * @param instance            the resolver instance, or null when the source or a per-call supplier is the target
     * @param suspendInvoker      starts the coroutine of a Kotlin {@code suspend} method and returns its stage, or null for a
     *                            method that is not {@code suspend}: such a field pays a null check and nothing more
     */
    public MicronautExecutableMethodDataFetcher(
            Executable<Object, ?> executable,
            List<ArgumentDefinition> argumentDefinitions,
            @Nullable Object instance,
            @Nullable SuspendInvoker suspendInvoker
    ) {
        this.executable = executable;
        this.suspendInvoker = suspendInvoker;
        int size = argumentDefinitions.size();
        this.kinds = new int[size];
        this.names = new String[size];
        this.converters = new ValueConverter[size];
        this.loaders = new int[size];
        this.principalTypes = principalTypes(size);
        boolean state = false;
        for (int i = 0; i < size; i++) {
            ArgumentDefinition argumentDefinition = argumentDefinitions.get(i);
            names[i] = argumentDefinition.getName();
            if (argumentDefinition.isSourceArgument()) {
                kinds[i] = SOURCE;
            } else if (argumentDefinition.isDataFetchingEnvironmentArgument()) {
                kinds[i] = DATA_FETCHING_ENVIRONMENT;
            } else if (argumentDefinition.isDataLoaderArgument()) {
                kinds[i] = DATA_LOADER;
                loaders[i] = argumentDefinition.getDataLoaderIndex();
            } else if (argumentDefinition.getKind() != SlotKind.ARGUMENT) {
                kinds[i] = switch (argumentDefinition.getKind()) {
                    case CONTEXT -> CONTEXT;
                    case CONTEXT_VALUE -> CONTEXT_VALUE;
                    case SELECTION_SET -> SELECTION_SET;
                    case LOCALE -> LOCALE;
                    case PRINCIPAL -> PRINCIPAL;
                    case REQUEST -> REQUEST;
                    case RESPONSE -> RESPONSE;
                    default -> OPTIONAL_RESPONSE;
                };
                principalTypes[i] = argumentDefinition.getPrincipalType();
                state |= kinds[i] >= PRINCIPAL;
            } else {
                Argument<?> argument = argumentDefinition.getInputValueArgument().orElseThrow();
                boolean argumentValue = ArgumentValue.class.equals(argument.getType());
                kinds[i] = argumentValue ? ARGUMENT_VALUE : INPUT_VALUE;
                ValueConverter converter = argumentDefinition.getConverter();
                converters[i] = converter == ValueConverters.IDENTITY ? null : converter;
            }
        }
        this.needsState = state;
        this.perCall = instance instanceof PerCallResolver resolver ? resolver : null;
        this.instance = perCall == null ? instance : null;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        int size = kinds.length;
        // a suspend method has one more parameter than the field has arguments: the continuation, which the invoker fills
        Object[] arguments = suspendInvoker != null ? new Object[size + 1] : size == 0 ? NO_ARGUMENTS : new Object[size];

        // read only by a plan that has a request, response or principal parameter: any other field touches no state
        ExecutionState state = needsState ? ExecutionState.of(environment.getGraphQlContext()) : null;
        int i = 0;
        try {
            for (; i < size; i++) {
                arguments[i] = argument(environment, state, i);
            }
        } catch (DataLoaderUnavailableException e) {
            return dataLoaderUnavailable(environment, e);
        } catch (RuntimeException e) {
            return inputConversionFailure(environment, names[i], e);
        }

        Object target = instance;
        if (target == null) {
            target = perCall != null ? perCall.supplier().get() : environment.getSource();
        }
        return suspendInvoker != null ? suspendInvoker.invoke(executable, target, arguments) : executable.invoke(target, arguments);
    }

    private Object argument(DataFetchingEnvironment environment, @Nullable ExecutionState state, int index) {
        return switch (kinds[index]) {
            case SOURCE -> environment.getSource();
            case DATA_FETCHING_ENVIRONMENT -> environment;
            case CONTEXT -> environment.getGraphQlContext();
            case CONTEXT_VALUE -> environment.getGraphQlContext().get(names[index]);
            case SELECTION_SET -> environment.getSelectionSet();
            case LOCALE -> environment.getLocale();
            case PRINCIPAL -> principal(state, principalTypes[index]);
            case REQUEST -> state == null ? null : state.httpRequest();
            case RESPONSE -> state == null ? null : state.response();
            case OPTIONAL_RESPONSE -> state == null ? Optional.empty() : state.httpResponse();
            case DATA_LOADER -> {
                // by index, from the registry of this execution: no string lookup, and the loader is created on first use
                if (environment.getDataLoaderRegistry() instanceof LazyDataLoaderRegistry registry) {
                    yield registry.loader(loaders[index]);
                }
                throw new DataLoaderUnavailableException();
            }
            case ARGUMENT_VALUE -> {
                // graphql-java leaves an argument out of the map only if the client did not provide it and the
                // schema declares no default value for it
                if (environment.containsArgument(names[index])) {
                    yield ArgumentValue.ofNullable(convert(index, environment.getArgument(names[index])));
                }
                yield ArgumentValue.omitted();
            }
            default -> convert(index, environment.getArgument(names[index]));
        };
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Principal>[] principalTypes(int size) {
        return (Class<? extends Principal>[]) new Class<?>[size];
    }

    @Nullable
    private static Object principal(@Nullable ExecutionState state, Class<? extends Principal> type) {
        HttpRequest<?> request = state == null ? null : state.httpRequest();
        return request == null ? null : request.getUserPrincipal(type).orElse(null);
    }

    private static DataFetcherResult<Object> dataLoaderUnavailable(DataFetchingEnvironment environment,
                                                                   DataLoaderUnavailableException failure) {
        ExecutionState state = ExecutionState.of(environment.getGraphQlContext());
        String customizer = state == null ? null : state.customizer();
        String coordinate = ((GraphQLNamedType) environment.getParentType()).getName() + "."
                + environment.getFieldDefinition().getName();
        LOG.error("{} needs a DataLoader of the library, and the DataLoaderRegistry of the ExecutionInput is not the library's: {}",
                coordinate, customizer == null
                        ? "something replaced it" : "the GraphQLExecutionInputCustomizer " + customizer + " replaced it");
        GraphQLError error = GraphqlErrorBuilder.newError(environment)
                .message("The DataLoader of " + coordinate + " is unavailable: the DataLoaderRegistry of this execution was replaced"
                        + (customizer == null ? "." : " by the GraphQLExecutionInputCustomizer " + customizer + "."))
                .extensions(Map.of("code", "DATA_LOADER_UNAVAILABLE"))
                .build();
        return DataFetcherResult.newResult().error(error).build();
    }

    private Object convert(int index, Object value) {
        ValueConverter converter = converters[index];
        return converter == null ? value : converter.convert(value);
    }

    /**
     * A value that does not convert becomes an error of the field, not an exception: the client gets the argument and
     * the problem in GraphQL terms, the log gets the cause.
     */
    private static DataFetcherResult<Object> inputConversionFailure(DataFetchingEnvironment environment, String argument,
                                                                    RuntimeException failure) {
        InputConversionException conversion = failure instanceof InputConversionException known ? known
                : new InputConversionException("the value could not be converted", String.valueOf(failure.getMessage()),
                failure);
        String coordinate = ((GraphQLNamedType) environment.getParentType()).getName() + "."
                + environment.getFieldDefinition().getName() + "(" + argument + ")";
        List<Object> inputPath = conversion.getPath();
        StringBuilder message = new StringBuilder("Invalid value for argument ").append(coordinate);
        if (!inputPath.isEmpty()) {
            message.append(" at ");
            for (Object place : inputPath) {
                if (place instanceof Integer) {
                    message.append('[').append(place).append(']');
                } else {
                    message.append(message.charAt(message.length() - 1) == ' ' ? "" : ".").append(place);
                }
            }
        }
        message.append(": ").append(conversion.getDetail()).append('.');
        if (LOG.isDebugEnabled()) {
            LOG.debug("{}: {}", message, conversion.getMessage(), failure);
        }
        GraphQLError error = GraphqlErrorBuilder.newError(environment)
                .message(message.toString())
                .extensions(Map.of("code", INPUT_CONVERSION_FAILED, "coordinate", coordinate, "inputPath", inputPath))
                .build();
        return DataFetcherResult.newResult().error(error).build();
    }

    /** The registry of the execution is not the library's: an application's customizer replaced it. */
    private static final class DataLoaderUnavailableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        DataLoaderUnavailableException() {
            super(null, null, false, false);
        }
    }

}
