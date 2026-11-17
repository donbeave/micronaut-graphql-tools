package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

@GraphQLRootResolver
public class BookQuery {

    @GraphQLSchemaCoordinate("Query.book")
    public Book book(String id) {
        return new Book(id, "Dune");
    }

    @GraphQLSchemaCoordinate("Missing.field")
    public String stray() {
        return "x";
    }

}
