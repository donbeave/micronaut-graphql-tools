package example

import io.micronaut.context.annotation.Replaces
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
@Replaces(LibraryQuery::class)
class ApplicationQuery {

    fun hello(): String = "application"

}
