package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    @GraphQLSchemaCoordinate("Query.shelf")
    public Book book(String id) {
        return new Book(id);
    }

    @GraphQLSchemaCoordinate("Query.books")
    public List<Book> books(Integer limit) {
        return List.of();
    }

}
