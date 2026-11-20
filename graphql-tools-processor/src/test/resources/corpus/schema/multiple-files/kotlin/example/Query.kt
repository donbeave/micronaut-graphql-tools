package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    fun book(id: String): Book = Book(id, "Dune")

    fun count(): Int = 1

}
