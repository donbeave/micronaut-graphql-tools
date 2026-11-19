package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    fun book(id: String, locale: String): Book = Book(id)

    fun books(limit: Int?): List<Book> = emptyList()

}
