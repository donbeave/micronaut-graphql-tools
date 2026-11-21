package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Book")
class Book implements SearchResult {

    Integer getTitle() {
        return 1
    }

}
