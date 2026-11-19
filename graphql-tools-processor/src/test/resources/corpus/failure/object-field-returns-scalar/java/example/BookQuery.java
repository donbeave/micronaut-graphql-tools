package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    public String book(String id) {
        return id;
    }

    public List<Book> books(Integer limit) {
        return List.of();
    }

}
