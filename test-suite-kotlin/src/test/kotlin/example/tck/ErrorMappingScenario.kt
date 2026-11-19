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
import graphql.GraphQLError
import graphql.language.SourceLocation
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerResult
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.customizer.GraphQLBuilderCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `error-mapping`: a DataFetcherExceptionHandler installed through a GraphQLBuilderCustomizer
// maps one exception type to an error with a code and hides every other message.

class NotFoundException(val id: Int) : RuntimeException("no item $id")

// GraphqlErrorBuilder is not used: its self-referencing generics overflow the Kotlin compiler on graphql-java 26.2.
class MappedError(
    private val text: String,
    private val errorPath: List<Any>,
    private val location: SourceLocation?,
    private val data: Map<String, Any>
) : GraphQLError {
    override fun getMessage(): String = text
    override fun getLocations(): List<SourceLocation> = listOfNotNull(location)
    override fun getErrorType(): ErrorType = ErrorType.DataFetchingException
    override fun getPath(): List<Any> = errorPath
    override fun getExtensions(): Map<String, Any> = data
}

@Singleton
@Requires(property = "tck.scenario", value = "error-mapping")
class ErrorMappingCustomizer : GraphQLBuilderCustomizer {
    override fun customize(builder: GraphQL.Builder) {
        val handler = DataFetcherExceptionHandler { parameters ->
            var cause: Throwable = parameters.exception
            if (cause is CompletionException && cause.cause != null) {
                cause = cause.cause!!
            }
            val error = if (cause is NotFoundException) {
                MappedError(cause.message!!, parameters.path.toList(), parameters.sourceLocation, mapOf("code" to "NOT_FOUND", "id" to cause.id))
            } else {
                MappedError("Internal error", parameters.path.toList(), parameters.sourceLocation, mapOf("code" to "INTERNAL"))
            }
            CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult(error).build())
        }
        builder.defaultDataFetcherExceptionHandler(handler)
    }
}

@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "error-mapping")
class ErrorMappingQueries {

    fun fine(): String = "fine"

    fun missing(id: Int): String? = throw NotFoundException(id)

    fun missingNonNull(id: Int): String = throw NotFoundException(id)

    fun missingLater(id: Int): CompletionStage<String?> = CompletableFuture.failedFuture(NotFoundException(id))

    fun crash(): String? = throw IllegalStateException("secret detail")
}
