package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class RenameQuery {

    fun rename(input: Dto): String = input.name

}
