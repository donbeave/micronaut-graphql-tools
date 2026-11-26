package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    suspend fun book(id: String): Book? = Book(id, "Dune")

}
