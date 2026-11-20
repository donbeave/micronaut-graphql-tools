package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate

@Singleton
@GraphQLRootResolver
class Query {

    @GraphQLSchemaCoordinate("Query.books")
    fun fetch(limit: Int): List<String> = emptyList()

    @GraphQLSchemaCoordinate("Query.version")
    fun release(): String = "1"

}
