package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.core.async.annotation.SingleResult;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    @SingleResult
    public org.reactivestreams.Publisher<Book> book(String id) {
        return null;
    }

    @SingleResult
    public reactor.core.publisher.Flux<Book> maybeBook(String id) {
        return null;
    }

}
