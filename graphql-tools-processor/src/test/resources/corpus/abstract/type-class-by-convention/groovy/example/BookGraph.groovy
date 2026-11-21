package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class BookGraph implements SearchResult {

    String title = 'title'

}
