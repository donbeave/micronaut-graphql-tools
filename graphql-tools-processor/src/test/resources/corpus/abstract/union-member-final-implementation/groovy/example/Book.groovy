package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Book")
final class Book implements SearchResult {

    String getTitle() {
        return "title"
    }

}
