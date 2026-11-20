package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public Book book(String id, io.micronaut.graphql.tools.input.ArgumentValue<String> title) {
        return null;
    }

    public List<Book> search(BookFilter filter) {
        return null;
    }

}
