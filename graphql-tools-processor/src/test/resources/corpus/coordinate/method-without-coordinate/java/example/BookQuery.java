package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    @GraphQLSchemaCoordinate("Query.book")
    public Book book(String id) {
        return new Book(id, "Dune");
    }

    public String helper() {
        return "not a field";
    }

}
