package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    public Book book(String id, io.micronaut.graphql.tools.ArgumentValue<String> title) {
        return null;
    }

    public List<Book> search(BookFilter filter) {
        return null;
    }

}
