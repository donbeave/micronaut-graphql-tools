package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    @GraphQLSchemaCoordinate("Query.book")
    public Book book(String id) {
        return new Book(id);
    }

    @GraphQLSchemaCoordinate("Mutation.book")
    public Book mutateBook(String id) {
        return new Book(id);
    }

    @GraphQLSchemaCoordinate("Query.books")
    public List<Book> books(Integer limit) {
        return List.of();
    }

}
