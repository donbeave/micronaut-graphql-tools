package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    fun book(id: String): Book = Book("t", Author("a"))

    fun books(): List<Book> = listOf(Book("t", Author("a")))

}
