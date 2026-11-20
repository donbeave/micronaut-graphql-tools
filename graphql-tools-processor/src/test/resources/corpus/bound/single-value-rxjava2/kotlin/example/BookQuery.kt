package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    fun book(id: String): io.reactivex.Single<Book>? = null

    fun maybeBook(id: String): io.reactivex.Maybe<Book>? = null

}
