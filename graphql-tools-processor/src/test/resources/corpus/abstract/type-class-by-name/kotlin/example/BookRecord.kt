package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Book")
class BookRecord : SearchResult {

    val title: String = "title"

}
