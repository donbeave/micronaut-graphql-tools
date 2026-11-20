package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class QueryResolver {

    fun stray(): String = ""

    fun ping(): String = "pong"

}
