package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    Book book(String id) {
        return null
    }

    List<Book> search(BookFilter filter) {
        return null
    }

}
