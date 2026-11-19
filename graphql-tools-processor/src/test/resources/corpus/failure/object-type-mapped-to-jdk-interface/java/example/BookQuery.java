package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    public List<Book> book(String id) {
        return null;
    }

    public List<String> search(BookFilter filter) {
        return null;
    }

}
