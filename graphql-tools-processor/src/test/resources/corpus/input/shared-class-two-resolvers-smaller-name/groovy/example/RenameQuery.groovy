package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class RenameQuery {

    String rename(Dto input) {
        return input.name
    }

}
