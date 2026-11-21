package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class BookModel : SearchResult {

    val title: String = "title"

}
