package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    fun book(id: String): io.micronaut.core.async.publisher.CompletableFuturePublisher<Book>? = null

    fun maybeBook(id: String): io.micronaut.core.async.publisher.CompletableFuturePublisher<Book>? = null

}
