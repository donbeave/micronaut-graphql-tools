package example;

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import jakarta.inject.Singleton;

@Singleton
public class BookMutation implements BookMutationContract {

    @Override
    @GraphQLSchemaCoordinate("Query.book")
    public Book rename(String id) {
        return new Book();
    }

}
