package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Book")
open class Book : SearchResult {

    val title: Int = 1

}
