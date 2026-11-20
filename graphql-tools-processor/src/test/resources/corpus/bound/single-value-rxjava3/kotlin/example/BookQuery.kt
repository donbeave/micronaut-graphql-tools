package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    fun book(id: String): io.reactivex.rxjava3.core.Single<Book>? = null

    fun maybeBook(id: String): io.reactivex.rxjava3.core.Maybe<Book>? = null

    fun done(): io.reactivex.rxjava3.core.Completable? = null

}
