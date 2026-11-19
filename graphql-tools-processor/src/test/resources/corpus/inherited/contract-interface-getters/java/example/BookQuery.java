package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    public BookGraph book(String id) {
        return null;
    }

    public List<BookGraph> search(BookFilter filter) {
        return null;
    }

}
