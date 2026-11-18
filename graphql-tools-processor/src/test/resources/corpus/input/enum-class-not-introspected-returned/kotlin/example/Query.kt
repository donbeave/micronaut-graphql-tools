package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    fun color(): Color = Color.RED

}
