package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    fun getBooks(limit: Int): List<String> = emptyList()

    fun getVersion(): String = "1"

}
