package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    public Book result() {
        return null;
    }

    public Book book(String id) {
        return null;
    }

    public List<Book> search(BookFilter filter) {
        return null;
    }

}
