package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    io.reactivex.Single<Book> book(String id) {
        return null
    }

    io.reactivex.Maybe<Book> maybeBook(String id) {
        return null
    }

}
