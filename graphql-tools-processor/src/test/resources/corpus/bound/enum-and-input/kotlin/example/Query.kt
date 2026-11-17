package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    fun level(): Level = Level.HIGH

    fun describe(filter: Filter): String = filter.text

}
