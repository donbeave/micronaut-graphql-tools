package example.books

import example.model.*
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    Book book(String id) {
        return new Book(id: id, title: 'Dune')
    }

    List<Book> search(BookFilter filter) {
        return [new Book(id: '1', title: filter.title)]
    }

    List<Book> books(Integer limit) {
        return [new Book(id: '1', title: 'Dune')]
    }

}
