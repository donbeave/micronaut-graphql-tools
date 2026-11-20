package example.books;

import jakarta.inject.Singleton;
import example.model.*;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public Book book(String id) {
        return new Book(id, "Dune");
    }

    public List<Book> search(BookFilter filter) {
        return List.of(new Book("1", filter.getTitle()));
    }

    public List<Book> books(Integer limit) {
        return List.of(new Book("1", "Dune"));
    }

}
