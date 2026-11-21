package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Book")
class Book : SearchResult {

    val title: String = "title"

}
