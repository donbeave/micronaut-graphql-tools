package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.core.async.annotation.SingleResult

@Singleton
@GraphQLRootResolver
class BookQuery {

    @SingleResult
    org.reactivestreams.Publisher<Book> book(String id) {
        return null
    }

    @SingleResult
    reactor.core.publisher.Flux<Book> maybeBook(String id) {
        return null
    }

}
