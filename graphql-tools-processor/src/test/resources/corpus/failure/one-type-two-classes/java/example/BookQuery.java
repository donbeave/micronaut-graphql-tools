package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public Book book(String id) {
        return null;
    }

    public List<Novel> search(BookFilter filter) {
        return null;
    }

}
