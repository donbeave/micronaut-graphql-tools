package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.core.async.annotation.SingleResult

@Singleton
@GraphQLRootResolver
class BookQuery {

    @SingleResult
    fun book(id: String): org.reactivestreams.Publisher<Book>? = null

    @SingleResult
    fun maybeBook(id: String): reactor.core.publisher.Flux<Book>? = null

}
