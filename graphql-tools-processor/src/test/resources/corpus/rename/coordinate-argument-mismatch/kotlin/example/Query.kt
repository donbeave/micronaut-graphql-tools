package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate

@GraphQLRootResolver
class Query {

    @GraphQLSchemaCoordinate("Query.books")
    fun fetch(limit: Int): List<String> = emptyList()

    @GraphQLSchemaCoordinate("Query.version")
    fun release(): String = "1"

}
