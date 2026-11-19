package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    public static String util() {
        return null;
    }

    @Override
    public String toString() {
        return "BookQuery";
    }

    public Book book(String id) {
        return new Book(id);
    }

    public List<Book> books(Integer limit) {
        return List.of();
    }

}
