package example

import io.micronaut.context.annotation.Replaces
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
@Replaces(LibraryQuery)
class ApplicationQuery {

    OptionalInt count() {
        return OptionalInt.of(3)
    }

}
