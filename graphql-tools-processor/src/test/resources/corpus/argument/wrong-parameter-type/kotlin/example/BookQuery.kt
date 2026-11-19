package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    fun book(id: String): Book = Book(id)

    fun books(limit: String?): List<Book> = emptyList()

}
