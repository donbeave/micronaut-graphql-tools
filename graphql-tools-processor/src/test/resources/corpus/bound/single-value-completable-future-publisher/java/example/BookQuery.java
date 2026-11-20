package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class BookQuery {

    public io.micronaut.core.async.publisher.CompletableFuturePublisher<Book> book(String id) {
        return null;
    }

    public io.micronaut.core.async.publisher.CompletableFuturePublisher<Book> maybeBook(String id) {
        return null;
    }

}
