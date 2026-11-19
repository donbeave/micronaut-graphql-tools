package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

import java.util.List;

@GraphQLRootResolver
public interface BookQueryContract {

    @GraphQLSchemaCoordinate("Query.book")
    Book book(String id);

    @GraphQLSchemaCoordinate("Query.books")
    List<Book> books(Integer limit);

}
