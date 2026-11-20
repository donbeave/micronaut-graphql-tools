package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    fun book(id: String): Book = Book(1L)

    fun books(limit: Int?): List<Book> = emptyList()

}
