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

import graphql.ErrorType
import graphql.GraphQL
import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerResult
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.GraphQLBuilderCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `error-mapping`: a DataFetcherExceptionHandler installed through a GraphQLBuilderCustomizer
// maps one exception type to an error with a code and hides every other message.

@CompileStatic
class NotFoundException extends RuntimeException {
    final int id

    NotFoundException(int id) {
        super('no item ' + id)
        this.id = id
    }
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'error-mapping')
class ErrorMappingCustomizer implements GraphQLBuilderCustomizer {
    @Override
    void customize(GraphQL.Builder builder) {
        DataFetcherExceptionHandler handler = { parameters ->
            Throwable cause = parameters.exception
            if (cause instanceof CompletionException && cause.cause != null) {
                cause = cause.cause
            }
            GraphqlErrorBuilder<?> error = GraphqlErrorBuilder.newError()
                    .path(parameters.path)
                    .location(parameters.sourceLocation)
                    .errorType(ErrorType.DataFetchingException)
            if (cause instanceof NotFoundException) {
                error.message(cause.message).extensions([code: 'NOT_FOUND', id: ((NotFoundException) cause).id] as Map<String, Object>)
            } else {
                error.message('Internal error').extensions([code: 'INTERNAL'] as Map<String, Object>)
            }
            return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult(error.build()).build())
        } as DataFetcherExceptionHandler
        builder.defaultDataFetcherExceptionHandler(handler)
    }
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'error-mapping')
class ErrorMappingQueries {

    String fine() {
        return 'fine'
    }

    String missing(int id) {
        throw new NotFoundException(id)
    }

    String missingNonNull(int id) {
        throw new NotFoundException(id)
    }

    CompletionStage<String> missingLater(int id) {
        return CompletableFuture.<String> failedFuture(new NotFoundException(id))
    }

    String crash() {
        throw new IllegalStateException('secret detail')
    }
}
