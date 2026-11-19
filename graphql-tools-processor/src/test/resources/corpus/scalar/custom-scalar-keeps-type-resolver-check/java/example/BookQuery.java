package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    public java.time.LocalDate today() {
        return null;
    }

    public Book book(String id) {
        return new Book(id);
    }

    public List<Book> books(Integer limit) {
        return List.of();
    }

}
