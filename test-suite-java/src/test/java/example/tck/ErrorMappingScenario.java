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
package example.tck;

import graphql.ErrorType;
import graphql.GraphQL;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerResult;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.customizer.GraphQLBuilderCustomizer;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * The fixtures of the TCK scenario {@code error-mapping}: a {@link DataFetcherExceptionHandler} installed through a
 * {@link GraphQLBuilderCustomizer} maps one exception type to an error with a code and hides every other message.
 */
public final class ErrorMappingScenario {

    private ErrorMappingScenario() {
    }

    public static class NotFoundException extends RuntimeException {
        private final int id;

        public NotFoundException(int id) {
            super("no item " + id);
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = "error-mapping")
    public static class Customizer implements GraphQLBuilderCustomizer {
        @Override
        public void customize(GraphQL.Builder builder) {
            DataFetcherExceptionHandler handler = parameters -> {
                Throwable cause = parameters.getException();
                if (cause instanceof CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                GraphqlErrorBuilder<?> error = GraphqlErrorBuilder.newError()
                        .path(parameters.getPath())
                        .location(parameters.getSourceLocation())
                        .errorType(ErrorType.DataFetchingException);
                if (cause instanceof NotFoundException notFound) {
                    error.message(notFound.getMessage()).extensions(Map.of("code", "NOT_FOUND", "id", notFound.getId()));
                } else {
                    error.message("Internal error").extensions(Map.of("code", "INTERNAL"));
                }
                return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult(error.build()).build());
            };
            builder.defaultDataFetcherExceptionHandler(handler);
        }
    }

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "error-mapping")
    public static class Queries {

        public String fine() {
            return "fine";
        }

        public String missing(int id) {
            throw new NotFoundException(id);
        }

        public String missingNonNull(int id) {
            throw new NotFoundException(id);
        }

        public CompletionStage<String> missingLater(int id) {
            return CompletableFuture.failedFuture(new NotFoundException(id));
        }

        public String crash() {
            throw new IllegalStateException("secret detail");
        }
    }

}
