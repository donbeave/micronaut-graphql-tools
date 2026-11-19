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
import io.micronaut.graphql.tools.input.ArgumentValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
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

    private final Executable<Object, ?> executable;
    private final int[] kinds;
    private final String[] names;
    private final ValueConverter[] converters;
    private final Object instance;
    private final PerCallResolver perCall;

    public MicronautExecutableMethodDataFetcher(
            Executable<Object, ?> executable,
            List<ArgumentDefinition> argumentDefinitions,
            @Nullable Object instance
    ) {
        this.executable = executable;
        int size = argumentDefinitions.size();
        this.kinds = new int[size];
        this.names = new String[size];
        this.converters = new ValueConverter[size];
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
                ValueConverter converter = argumentDefinition.getConverter();
                converters[i] = converter == ValueConverters.IDENTITY ? null : converter;
            }
        }
        this.perCall = instance instanceof PerCallResolver resolver ? resolver : null;
        this.instance = perCall == null ? instance : null;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        int size = kinds.length;
        Object[] arguments = size == 0 ? NO_ARGUMENTS : new Object[size];

        int i = 0;
        try {
            for (; i < size; i++) {
                arguments[i] = argument(environment, i);
            }
        } catch (RuntimeException e) {
            return inputConversionFailure(environment, names[i], e);
        }

        Object target = instance;
        if (target == null) {
            target = perCall != null ? perCall.supplier().get() : environment.getSource();
        }
        return executable.invoke(target, arguments);
    }

    private Object argument(DataFetchingEnvironment environment, int index) {
        return switch (kinds[index]) {
            case SOURCE -> environment.getSource();
            case DATA_FETCHING_ENVIRONMENT -> environment;
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

}
