package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    fun book(id: String): Book = Book(id, "Dune")

    fun count(): Int = 1

}
