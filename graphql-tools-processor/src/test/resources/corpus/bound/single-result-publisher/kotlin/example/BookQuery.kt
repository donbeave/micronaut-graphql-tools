package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.core.async.annotation.SingleResult

@GraphQLRootResolver
class BookQuery {

    @SingleResult
    fun book(id: String): org.reactivestreams.Publisher<Book>? = null

    @SingleResult
    fun maybeBook(id: String): reactor.core.publisher.Flux<Book>? = null

}
