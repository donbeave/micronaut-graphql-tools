package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@Singleton
@GraphQLRootResolver
class Query {

    suspend fun book(id: String): CompletionStage<Book?> = CompletableFuture.completedFuture(Book(id, "Dune"))

    suspend fun books(limit: Int?): List<Book> = emptyList()

    suspend fun count(): Int = 1

}
