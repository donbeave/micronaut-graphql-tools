package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    io.reactivex.Single<Book> book(String id) {
        return null
    }

    io.reactivex.Maybe<Book> maybeBook(String id) {
        return null
    }

    io.reactivex.Completable done() {
        return null
    }

}
