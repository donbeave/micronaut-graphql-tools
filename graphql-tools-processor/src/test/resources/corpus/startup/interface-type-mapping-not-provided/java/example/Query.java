package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLRootResolver
public class Query {

    public Book book(String id) {
        return null;
    }

}
