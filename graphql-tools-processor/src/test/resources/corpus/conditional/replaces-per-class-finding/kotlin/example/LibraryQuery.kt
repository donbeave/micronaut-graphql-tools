package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
open class LibraryQuery {

    fun count(): Int = 1

}
