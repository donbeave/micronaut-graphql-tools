package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    Book book(String id) {
        return new Book(title: id)
    }

    List<Book> books(int limit) {
        return []
    }

}
