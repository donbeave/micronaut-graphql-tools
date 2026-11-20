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
import graphql.GraphqlErrorException
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

open class OutOfStockException(val id: Int) : RuntimeException("no stock for item $id")

class SoldOutException : OutOfStockException(0)

@Requires(property = "tck.scenario", pattern = "error-(handling|details)")
// tag::mapper[]
@Singleton
@Order(10)
class OutOfStockMapper : GraphQLExceptionMapper<OutOfStockException> {
    override fun map(exception: OutOfStockException, context: GraphQLExceptionContext): GraphQLError =
        context.error(exception.message!!, mapOf("code" to "OUT_OF_STOCK", "id" to exception.id))
}
// end::mapper[]

@Singleton
@Order(0)
@Requires(property = "tck.scenario", pattern = "error-(handling|details)")
class SoldOutMapper : GraphQLExceptionMapper<SoldOutException> {
    override fun map(exception: SoldOutException, context: GraphQLExceptionContext): GraphQLError =
        context.error("sold out", mapOf("code" to "SOLD_OUT"))
}

@Requires(property = "tck.scenario", value = "error-handling")
// tag::customizer[]
@Singleton
class ServiceNameCustomizer : GraphQLErrorsCustomizer {
    override fun customize(errors: List<GraphQLError>): List<GraphQLError> = errors.map { error ->
        MappedError(error.message, error.path ?: emptyList(), error.locations?.firstOrNull(), (error.extensions ?: emptyMap()) + ("service" to "catalog"))
    }
}
// end::customizer[]

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", pattern = "error-(handling|details)")
class ErrorHandlingQueries {

    fun stock(): String = "in stock"

    fun stockBoom(): String? = throw IllegalStateException("secret detail")

    fun stockBoomNonNull(): String = throw IllegalStateException("secret detail")

    fun stockMissing(id: Int): String? = throw OutOfStockException(id)

    fun stockMissingLater(id: Int): CompletionStage<String?> = CompletableFuture.failedFuture(OutOfStockException(id))

    fun stockBoomLater(): CompletionStage<String?> = CompletableFuture.failedFuture(IllegalStateException("secret later detail"))

    fun stockRejected(): String? = throw GraphqlErrorException.newErrorException().message("Not allowed").extensions(mapOf("reason" to "policy")).build()

    fun stockSoldOut(): String? = throw SoldOutException()
}
