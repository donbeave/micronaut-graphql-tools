package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    fun book(id: String): io.micronaut.core.async.publisher.CompletableFuturePublisher<Book>? = null

    fun maybeBook(id: String): io.micronaut.core.async.publisher.CompletableFuturePublisher<Book>? = null

}
