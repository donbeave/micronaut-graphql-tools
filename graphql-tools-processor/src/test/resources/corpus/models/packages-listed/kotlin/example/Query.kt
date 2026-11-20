package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import example.model.Book
import example.model.Author

@GraphQLRootResolver
class Query {

    fun book(id: String): Book = Book("t", Author("a"))

    fun books(): List<Book> = listOf(Book("t", Author("a")))

}
