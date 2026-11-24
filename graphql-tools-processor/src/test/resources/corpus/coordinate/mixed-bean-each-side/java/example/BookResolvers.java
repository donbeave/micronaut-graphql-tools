package example;

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import jakarta.inject.Singleton;

@Singleton
public class BookResolvers implements BookQueryContract, BookSummaryContract {

    @Override
    @GraphQLSchemaCoordinate("Query.book")
    public Book lookup(String id) {
        return new Book();
    }

    @Override
    @GraphQLSchemaCoordinate("Book.summary")
    public String describe(Book book) {
        return "s";
    }

}
