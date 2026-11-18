package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    fun paint(colors: List<Color>): Int = colors.size

}
