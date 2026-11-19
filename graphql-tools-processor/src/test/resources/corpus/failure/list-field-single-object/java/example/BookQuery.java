package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    public Book book(String id) {
        return new Book(id);
    }

    public Book books(Integer limit) {
        return null;
    }

}
