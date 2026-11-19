package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.Optional

@GraphQLRootResolver
class BookQuery {

    fun book(id: String): Book = Book(Optional.of(id))

    fun books(limit: Int?): List<Book> = emptyList()

}
