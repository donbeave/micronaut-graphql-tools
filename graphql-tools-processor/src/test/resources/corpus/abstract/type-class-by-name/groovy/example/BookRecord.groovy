package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = 'Book')
class BookRecord implements SearchResult {

    String title = 'title'

}
