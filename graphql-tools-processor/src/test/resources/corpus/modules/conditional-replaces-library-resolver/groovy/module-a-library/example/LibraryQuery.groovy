package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class LibraryQuery {

    String hello() {
        return 'library'
    }

}
