package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class Query {

    public Book book(String id) {
        return new Book("t", new Author("a"));
    }

    public List<Book> books() {
        return List.of(new Book("t", new Author("a")));
    }

}
