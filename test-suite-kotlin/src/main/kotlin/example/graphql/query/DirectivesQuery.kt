package example.graphql.query

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class DirectivesQuery {

    fun shout(): String = "quiet"

    fun tagged(): String = "value"
}
