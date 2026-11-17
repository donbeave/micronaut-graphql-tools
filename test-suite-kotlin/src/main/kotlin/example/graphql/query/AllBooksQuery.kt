package example.graphql.query

import example.ElapsedTime
import example.data.Book
import example.data.BookRepository
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

// tag::resolver[]
@GraphQLRootResolver
open class AllBooksQuery(private val bookRepository: BookRepository) {

    @ElapsedTime
    open fun allBooks(): List<Book> = bookRepository.findAll()
}
// end::resolver[]
