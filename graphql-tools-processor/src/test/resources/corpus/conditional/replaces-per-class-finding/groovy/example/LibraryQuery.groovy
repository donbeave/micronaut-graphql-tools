package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class LibraryQuery {

    int count() {
        return 1
    }

}
