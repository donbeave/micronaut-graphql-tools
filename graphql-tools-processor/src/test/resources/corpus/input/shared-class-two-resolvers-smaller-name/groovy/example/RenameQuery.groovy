package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class RenameQuery {

    String rename(Dto input) {
        return input.name
    }

}
