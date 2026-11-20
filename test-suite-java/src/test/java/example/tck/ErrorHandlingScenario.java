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

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.GraphqlErrorException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.customizer.GraphQLErrorsCustomizer;
import io.micronaut.graphql.tools.error.GraphQLExceptionContext;
import io.micronaut.graphql.tools.error.GraphQLExceptionMapper;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The fixtures of the TCK scenarios {@code error-handling} and {@code error-details}: the default error handling hides the
 * message of an exception, ordered {@link GraphQLExceptionMapper} beans choose the message and the extensions of the exceptions
 * they know, and a {@link GraphQLErrorsCustomizer} changes the final list. The {@code error-details} scenario sets
 * {@code micronaut.graphql.tools.errors.details}.
 */
public final class ErrorHandlingScenario {

    private ErrorHandlingScenario() {
    }

    public static class OutOfStockException extends RuntimeException {
        private final int id;

        public OutOfStockException(int id) {
            super("no stock for item " + id);
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    public static class SoldOutException extends OutOfStockException {
        public SoldOutException() {
            super(0);
        }
    }

    @Requires(property = "tck.scenario", pattern = "error-(handling|details)")
    // tag::mapper[]
    @Singleton
    @Order(10)
    public static class OutOfStockMapper implements GraphQLExceptionMapper<OutOfStockException> {
        @Override
        public GraphQLError map(OutOfStockException exception, GraphQLExceptionContext context) {
            return context.error(exception.getMessage(), Map.of("code", "OUT_OF_STOCK", "id", exception.getId()));
        }
    }
    // end::mapper[]

    @Singleton
    @Order(0)
    @Requires(property = "tck.scenario", pattern = "error-(handling|details)")
    public static class SoldOutMapper implements GraphQLExceptionMapper<SoldOutException> {
        @Override
        public GraphQLError map(SoldOutException exception, GraphQLExceptionContext context) {
            return context.error("sold out", Map.of("code", "SOLD_OUT"));
        }
    }

    @Requires(property = "tck.scenario", value = "error-handling")
    // tag::customizer[]
    @Singleton
    public static class ServiceNameCustomizer implements GraphQLErrorsCustomizer {
        @Override
        public List<GraphQLError> customize(List<GraphQLError> errors) {
            return errors.stream().map(error -> {
                Map<String, Object> extensions = new LinkedHashMap<>(error.getExtensions() == null ? Map.of() : error.getExtensions());
                extensions.put("service", "catalog");
                return GraphqlErrorBuilder.newError()
                        .message(error.getMessage()).path(error.getPath()).locations(error.getLocations())
                        .errorType(error.getErrorType()).extensions(extensions).build();
            }).toList();
        }
    }
    // end::customizer[]

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", pattern = "error-(handling|details)")
    public static class Queries {

        public String stock() {
            return "in stock";
        }

        public String stockBoom() {
            throw new IllegalStateException("secret detail");
        }

        public String stockBoomNonNull() {
            throw new IllegalStateException("secret detail");
        }

        public String stockMissing(int id) {
            throw new OutOfStockException(id);
        }

        public CompletionStage<String> stockMissingLater(int id) {
            return CompletableFuture.failedFuture(new OutOfStockException(id));
        }

        public CompletionStage<String> stockBoomLater() {
            return CompletableFuture.failedFuture(new IllegalStateException("secret later detail"));
        }

        public String stockRejected() {
            throw GraphqlErrorException.newErrorException().message("Not allowed").extensions(Map.of("reason", "policy")).build();
        }

        public String stockSoldOut() {
            throw new SoldOutException();
        }
    }

}
