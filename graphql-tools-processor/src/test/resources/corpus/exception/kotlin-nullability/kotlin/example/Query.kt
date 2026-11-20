package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    fun title(): String? = null

    fun search(term: String): String? = term

    fun describe(filter: Filter): String? = filter.text

    fun names(): List<String?> = emptyList()

    fun subtitle(): String = "subtitle"

}
