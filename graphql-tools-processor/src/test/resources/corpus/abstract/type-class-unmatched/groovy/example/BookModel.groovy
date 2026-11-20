package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class BookModel implements SearchResult {

    String title = 'title'

}
