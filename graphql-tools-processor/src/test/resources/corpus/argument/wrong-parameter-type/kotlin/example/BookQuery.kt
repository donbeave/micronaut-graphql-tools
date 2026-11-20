package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    fun book(id: String): Book = Book(id)

    fun books(limit: String?): List<Book> = emptyList()

}
