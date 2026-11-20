package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    io.micronaut.core.async.publisher.CompletableFuturePublisher<Book> book(String id) {
        return null
    }

    io.micronaut.core.async.publisher.CompletableFuturePublisher<Book> maybeBook(String id) {
        return null
    }

}
