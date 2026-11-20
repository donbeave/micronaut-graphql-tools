package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public List<Book> book(String id) {
        return null;
    }

    public List<String> search(BookFilter filter) {
        return null;
    }

}
