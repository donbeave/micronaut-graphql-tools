package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    fun getBooks(limit: Int): List<String> = emptyList()

    fun getVersion(): String = "1"

}
