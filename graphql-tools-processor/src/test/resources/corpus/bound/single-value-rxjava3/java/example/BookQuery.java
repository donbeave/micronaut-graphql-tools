package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class BookQuery {

    public io.reactivex.rxjava3.core.Single<Book> book(String id) {
        return null;
    }

    public io.reactivex.rxjava3.core.Maybe<Book> maybeBook(String id) {
        return null;
    }

}
