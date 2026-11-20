package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public org.reactivestreams.Publisher<Book> books(Integer limit) {
        return null;
    }

}
