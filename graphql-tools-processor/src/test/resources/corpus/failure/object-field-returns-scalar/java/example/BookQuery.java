package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public String book(String id) {
        return id;
    }

    public List<Book> books(Integer limit) {
        return List.of();
    }

}
