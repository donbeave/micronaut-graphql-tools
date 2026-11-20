package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.Optional

@Singleton
@GraphQLRootResolver
class BookQuery {

    fun book(id: String): Book = Book(Optional.of(id))

    fun books(limit: Int?): List<Book> = emptyList()

}
