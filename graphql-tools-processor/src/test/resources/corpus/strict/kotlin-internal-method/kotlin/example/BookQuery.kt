package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    internal fun stray(): String? = null

    fun book(id: String): Book = Book(id)

    fun books(limit: Int?): List<Book> = emptyList()

}
