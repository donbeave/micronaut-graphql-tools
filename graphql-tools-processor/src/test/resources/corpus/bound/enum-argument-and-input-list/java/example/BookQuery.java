package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public Book book(String id, Genre genre) {
        return null;
    }

    public List<Book> search(List<BookFilter> filter) {
        return null;
    }

}
