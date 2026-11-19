package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
open class LibraryQuery {

    fun hello(): String = "library"

}
