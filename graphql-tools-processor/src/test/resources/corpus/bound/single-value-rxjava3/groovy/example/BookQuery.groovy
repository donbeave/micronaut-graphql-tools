package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    io.reactivex.rxjava3.core.Single<Book> book(String id) {
        return null
    }

    io.reactivex.rxjava3.core.Maybe<Book> maybeBook(String id) {
        return null
    }

}
