package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class QueryResolver {

    fun stray(): String = ""

    fun ping(): String = "pong"

}
