package example

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Replaces
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
@Replaces(LibraryQuery)
class ApplicationQuery {

    String hello() {
        return 'application'
    }

}
