package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.core.async.annotation.SingleResult;

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
