package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    public BookView book(String id) {
        return null;
    }

    public List<BookView> search(BookFilter filter) {
        return null;
    }

}
