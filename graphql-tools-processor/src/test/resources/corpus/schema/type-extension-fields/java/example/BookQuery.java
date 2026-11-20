package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public String shelf() {
        return null;
    }

    public Book book(String id) {
        return new Book(id);
    }

    public List<Book> books(Integer limit) {
        return List.of();
    }

}
