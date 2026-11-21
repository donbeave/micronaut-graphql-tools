package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class BookGraph : SearchResult {

    val title: String = "title"

}
