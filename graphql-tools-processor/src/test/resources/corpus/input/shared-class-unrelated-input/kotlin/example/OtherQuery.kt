package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class OtherQuery {

    fun other(input: Extra): String = input.extra

}
