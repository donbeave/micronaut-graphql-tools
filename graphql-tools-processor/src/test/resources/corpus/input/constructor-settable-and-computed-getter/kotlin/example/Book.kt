package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Book {

    val title: String? = null
    val genre: Genre = Genre.FICTION

}
