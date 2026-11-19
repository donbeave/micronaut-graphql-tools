package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Book {

    String getTitle() {
        return null
    }

    Genre getGenre() {
        return null
    }

}
