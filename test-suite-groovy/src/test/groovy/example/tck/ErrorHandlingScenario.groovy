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
package example.tck

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.GraphqlErrorException
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.customizer.GraphQLErrorsCustomizer
import io.micronaut.graphql.tools.error.GraphQLExceptionContext
import io.micronaut.graphql.tools.error.GraphQLExceptionMapper
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenarios `error-handling` and `error-details`: the default error handling hides the message of an
// exception, ordered GraphQLExceptionMapper beans choose the message and the extensions of the exceptions they know, and a
// GraphQLErrorsCustomizer changes the final list. The `error-details` scenario sets micronaut.graphql.tools.errors.details.

@CompileStatic
class OutOfStockException extends RuntimeException {
    final int id

    OutOfStockException(int id) {
        super('no stock for item ' + id)
        this.id = id
    }
}

@CompileStatic
class SoldOutException extends OutOfStockException {
    SoldOutException() {
        super(0)
    }
}

@CompileStatic
@Requires(property = 'tck.scenario', pattern = 'error-(handling|details)')
// tag::mapper[]
@Singleton
@Order(10)
class OutOfStockMapper implements GraphQLExceptionMapper<OutOfStockException> {
    @Override
    GraphQLError map(OutOfStockException exception, GraphQLExceptionContext context) {
        context.error(exception.message, [code: 'OUT_OF_STOCK', id: exception.id] as Map<String, Object>)
    }
}
// end::mapper[]

@CompileStatic
@Singleton
@Order(0)
@Requires(property = 'tck.scenario', pattern = 'error-(handling|details)')
class SoldOutMapper implements GraphQLExceptionMapper<SoldOutException> {
    @Override
    GraphQLError map(SoldOutException exception, GraphQLExceptionContext context) {
        context.error('sold out', [code: 'SOLD_OUT'] as Map<String, Object>)
    }
}

@CompileStatic
@Requires(property = 'tck.scenario', value = 'error-handling')
// tag::customizer[]
@Singleton
class ServiceNameCustomizer implements GraphQLErrorsCustomizer {
    @Override
    List<GraphQLError> customize(List<GraphQLError> errors) {
        errors.collect { GraphQLError error ->
            Map<String, Object> extensions = new LinkedHashMap<>(error.extensions ?: [:])
            extensions.put('service', 'catalog')
            GraphqlErrorBuilder.newError()
                    .message(error.message).path(error.path).locations(error.locations)
                    .errorType(error.errorType).extensions(extensions).build()
        }
    }
}
// end::customizer[]

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', pattern = 'error-(handling|details)')
class ErrorHandlingQueries {

    String stock() {
        return 'in stock'
    }

    String stockBoom() {
        throw new IllegalStateException('secret detail')
    }

    String stockBoomNonNull() {
        throw new IllegalStateException('secret detail')
    }

    String stockMissing(int id) {
        throw new OutOfStockException(id)
    }

    CompletionStage<String> stockMissingLater(int id) {
        return CompletableFuture.<String> failedFuture(new OutOfStockException(id))
    }

    CompletionStage<String> stockBoomLater() {
        return CompletableFuture.<String> failedFuture(new IllegalStateException('secret later detail'))
    }

    String stockRejected() {
        throw GraphqlErrorException.newErrorException().message('Not allowed').extensions([reason: 'policy'] as Map<String, Object>).build()
    }

    String stockSoldOut() {
        throw new SoldOutException()
    }
}
