package example.graphql.query

import example.ElapsedTime
import example.data.Book
import example.data.BookRepository
import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@CompileStatic
@GraphQLRootResolver
class AllBooksQuery {

    private final BookRepository bookRepository

    AllBooksQuery(BookRepository bookRepository) {
        this.bookRepository = bookRepository
    }

    @ElapsedTime
    List<Book> allBooks() {
        bookRepository.findAll()
    }
}
