package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class BookQuery {

    public org.reactivestreams.Publisher<Book> books(Integer limit) {
        return null;
    }

}
