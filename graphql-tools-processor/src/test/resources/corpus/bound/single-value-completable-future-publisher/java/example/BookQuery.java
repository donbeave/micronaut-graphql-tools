package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public io.micronaut.core.async.publisher.CompletableFuturePublisher<Book> book(String id) {
        return null;
    }

    public io.micronaut.core.async.publisher.CompletableFuturePublisher<Book> maybeBook(String id) {
        return null;
    }

}
