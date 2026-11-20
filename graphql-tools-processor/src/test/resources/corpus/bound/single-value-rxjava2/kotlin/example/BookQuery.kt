package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    fun book(id: String): io.reactivex.Single<Book>? = null

    fun maybeBook(id: String): io.reactivex.Maybe<Book>? = null

    fun done(): io.reactivex.Completable? = null

}
