package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public org.reactivestreams.Publisher<Book> book(String id) {
        return null;
    }

    public List<Book> books(Integer limit) {
        return List.of();
    }

}
