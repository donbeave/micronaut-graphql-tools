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
package io.micronaut.graphql.tools.internal.error;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.error.GraphQLExceptionMapper;
import io.micronaut.graphql.tools.internal.execution.AsyncBoundaryDataFetcher;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The library's {@link DataFetcherExceptionHandler}, installed before the {@code GraphQLBuilderCustomizer} beans run, so
 * that a customizer can still replace it. For the exception that a field threw, with a wrapping
 * {@code CompletionException} or {@code ExecutionException} removed first:
 * <ol>
 * <li>the {@link GraphQLExceptionMapper} beans that handle its class, lowest order first: the first one that returns
 * errors decides (logged at DEBUG);</li>
 * <li>an exception that is a {@link GraphQLError} is the error, with the path and the location of the field added when it has
 * none (logged at DEBUG);</li>
 * <li>any other exception is logged once at ERROR, and the client gets the generic error {@code INTERNAL_ERROR} without the
 * message, or with it when {@code micronaut.graphql.tools.errors.details} is on.</li>
 * </ol>
 * The mappers of an exception class are looked up once and cached. Nothing here runs unless a field fails.
 */
@Internal
@Singleton
public final class LibraryDataFetcherExceptionHandler implements DataFetcherExceptionHandler {

    /** The code of the generic error and of a contributor failure. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    /** The message of the generic error. */
    public static final String GENERIC_MESSAGE = "Internal server error";

    private static final Logger LOG = LoggerFactory.getLogger(LibraryDataFetcherExceptionHandler.class);

    private final boolean details;
    private final boolean stackTraces;
    private final Registered[] registered;
    private final ClassValue<GraphQLExceptionMapper<Throwable>[]> mappers = new ClassValue<>() {
        @Override
        @SuppressWarnings("unchecked")
        protected GraphQLExceptionMapper<Throwable>[] computeValue(Class<?> type) {
            List<GraphQLExceptionMapper<Throwable>> matching = new ArrayList<>();
            for (Registered candidate : registered) {
                if (candidate.type().isAssignableFrom(type)) {
                    matching.add((GraphQLExceptionMapper<Throwable>) candidate.mapper());
                }
            }
            return matching.toArray(new GraphQLExceptionMapper[0]);
        }
    };

    @SuppressWarnings({"rawtypes", "unchecked"})
    LibraryDataFetcherExceptionHandler(List<BeanRegistration<GraphQLExceptionMapper>> mapperBeans,
                                       GraphQLToolsConfiguration configuration) {
        this.details = configuration.getErrors().isDetails();
        this.stackTraces = configuration.getErrors().isStackTraces();
        List<BeanRegistration<GraphQLExceptionMapper>> sorted = new ArrayList<>(mapperBeans);
        sorted.sort(Comparator.comparingInt(BeanRegistration::getOrder));
        this.registered = sorted.stream().map(LibraryDataFetcherExceptionHandler::register).toArray(Registered[]::new);
    }

    @SuppressWarnings("rawtypes")
    private static Registered register(BeanRegistration<GraphQLExceptionMapper> registration) {
        List<Argument<?>> arguments = registration.getBeanDefinition().getTypeArguments(GraphQLExceptionMapper.class);
        Class<?> type = arguments.isEmpty() ? Throwable.class : arguments.get(0).getType();
        return new Registered(Throwable.class.isAssignableFrom(type) ? type : Throwable.class, registration.getBean());
    }

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters parameters) {
        Throwable exception = AsyncBoundaryDataFetcher.unwrap(parameters.getException());
        FieldFailure context = new FieldFailure(parameters);
        List<GraphQLError> errors = mapped(exception, context);
        if (errors == null) {
            errors = List.of(exception instanceof GraphQLError error ? passThrough(error, context) : hidden(exception, context));
        }
        return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult().errors(errors).build());
    }

    private List<GraphQLError> mapped(Throwable exception, FieldFailure context) {
        for (GraphQLExceptionMapper<Throwable> mapper : mappers.get(exception.getClass())) {
            try {
                List<GraphQLError> errors = mapper.mapAll(exception, context);
                if (errors != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} at {} is mapped to {} error(s) by {}", exception.getClass().getName(), context.getPath(),
                                errors.size(), mapper.getClass().getName());
                    }
                    return errors;
                }
            } catch (RuntimeException e) {
                LOG.error("The GraphQLExceptionMapper {} failed for {} at {}; the next mapper is tried", mapper.getClass().getName(),
                        exception.getClass().getName(), context.getPath(), e);
            }
        }
        return null;
    }

    private static GraphQLError passThrough(GraphQLError error, FieldFailure context) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} at {} is a GraphQLError and is sent as it is", error.getClass().getName(), context.getPath());
        }
        boolean hasPath = error.getPath() != null && !error.getPath().isEmpty();
        boolean hasLocations = error.getLocations() != null && !error.getLocations().isEmpty();
        if (hasPath && hasLocations) {
            return error;
        }
        GraphqlErrorBuilder<?> builder = GraphqlErrorBuilder.newError()
                .message(error.getMessage())
                .errorType(error.getErrorType() == null ? ErrorType.DataFetchingException : error.getErrorType())
                .extensions(error.getExtensions());
        if (hasPath) {
            builder.path(error.getPath());
        } else {
            builder.path(context.getPath());
        }
        if (hasLocations) {
            builder.locations(error.getLocations());
        } else if (context.getSourceLocation() != null) {
            builder.location(context.getSourceLocation());
        }
        return builder.build();
    }

    private GraphQLError hidden(Throwable exception, FieldFailure context) {
        if (stackTraces) {
            LOG.error("Unhandled exception at {}: {}", context.getPath(), exception.toString(), exception);
        } else {
            LOG.error("Unhandled exception at {}: {}", context.getPath(), exception.toString());
        }
        GraphqlErrorBuilder<?> builder = context.newError();
        if (details) {
            return builder.message(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage())
                    .extensions(Map.of("code", INTERNAL_ERROR, "exceptionClass", exception.getClass().getName()))
                    .build();
        }
        return builder.message(GENERIC_MESSAGE).extensions(Map.of("code", INTERNAL_ERROR)).build();
    }

    private record Registered(Class<?> type, GraphQLExceptionMapper<?> mapper) {
    }

}
