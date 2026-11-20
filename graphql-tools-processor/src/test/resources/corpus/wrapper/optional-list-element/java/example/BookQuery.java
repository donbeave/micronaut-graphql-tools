package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public Book book(String id) {
        return new Book(id);
    }

    public java.util.concurrent.CompletionStage<List<java.util.Optional<Book>>> books(Integer limit) {
        return null;
    }

}
