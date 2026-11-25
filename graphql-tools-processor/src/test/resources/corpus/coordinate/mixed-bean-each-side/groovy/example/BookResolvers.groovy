package example

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import jakarta.inject.Singleton

@Singleton
class BookResolvers implements BookQueryContract, BookSummaryContract {

    @Override
    @GraphQLSchemaCoordinate('Query.book')
    Book lookup(String id) {
        return new Book()
    }

    @Override
    @GraphQLSchemaCoordinate('Book.summary')
    String describe(Book book) {
        return 's'
    }

}
