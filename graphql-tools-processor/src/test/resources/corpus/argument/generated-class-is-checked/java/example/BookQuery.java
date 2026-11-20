package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@javax.annotation.processing.Generated("some.codegen.Tool")
@Singleton
@GraphQLRootResolver
public class BookQuery {

    public Book book(String id, String locale) {
        return new Book(id);
    }

    public List<Book> books(Integer limit) {
        return List.of();
    }

}
