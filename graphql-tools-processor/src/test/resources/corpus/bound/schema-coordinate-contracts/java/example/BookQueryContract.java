package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

@GraphQLRootResolver
public interface BookQueryContract {

    @GraphQLSchemaCoordinate("Query.book")
    Book book(String id);

}
