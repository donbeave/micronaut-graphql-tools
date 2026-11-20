package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Book {

    final String title

    Book(String title) {
        this.title = title
    }

}
