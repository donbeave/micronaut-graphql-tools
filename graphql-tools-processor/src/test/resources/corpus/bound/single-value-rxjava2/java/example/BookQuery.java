package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public io.reactivex.Single<Book> book(String id) {
        return null;
    }

    public io.reactivex.Maybe<Book> maybeBook(String id) {
        return null;
    }

    public io.reactivex.Completable done() {
        return null;
    }

}
