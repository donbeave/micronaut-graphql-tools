package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    io.reactivex.rxjava3.core.Single<Book> book(String id) {
        return null
    }

    io.reactivex.rxjava3.core.Maybe<Book> maybeBook(String id) {
        return null
    }

    io.reactivex.rxjava3.core.Completable done() {
        return null
    }

}
