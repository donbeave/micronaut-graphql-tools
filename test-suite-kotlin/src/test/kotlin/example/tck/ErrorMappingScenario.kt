package example.tck

import graphql.ErrorType
import graphql.GraphQL
import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerResult
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.GraphQLBuilderCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `error-mapping`: a DataFetcherExceptionHandler installed through a GraphQLBuilderCustomizer
// maps one exception type to an error with a code and hides every other message.

class NotFoundException(val id: Int) : RuntimeException("no item $id")

@Singleton
@Requires(property = "tck.scenario", value = "error-mapping")
class ErrorMappingCustomizer : GraphQLBuilderCustomizer {
    override fun customize(builder: GraphQL.Builder) {
        val handler = DataFetcherExceptionHandler { parameters ->
            var cause: Throwable = parameters.exception
            if (cause is CompletionException && cause.cause != null) {
                cause = cause.cause!!
            }
            val error = GraphqlErrorBuilder.newError()
                .path(parameters.path)
                .location(parameters.sourceLocation)
                .errorType(ErrorType.DataFetchingException)
            if (cause is NotFoundException) {
                error.message(cause.message).extensions(mapOf("code" to "NOT_FOUND", "id" to cause.id))
            } else {
                error.message("Internal error").extensions(mapOf("code" to "INTERNAL"))
            }
            CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult(error.build()).build())
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
