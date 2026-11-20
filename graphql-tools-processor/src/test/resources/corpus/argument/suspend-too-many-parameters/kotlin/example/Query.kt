package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    suspend fun book(id: String, locale: String): Book? = Book(id, "Dune")

    suspend fun books(limit: Int?): List<Book> = emptyList()

    suspend fun count(): Int = 1

}
