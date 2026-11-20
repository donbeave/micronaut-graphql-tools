package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class BookQuery {

    public io.reactivex.Single<Book> book(String id) {
        return null;
    }

    public io.reactivex.Maybe<Book> maybeBook(String id) {
        return null;
    }

}
