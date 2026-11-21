package example;

import io.micronaut.graphql.tools.resolver.QueryResolver;
import jakarta.inject.Singleton;

@Singleton
public class BookQuery implements QueryResolver {

    public Book book(String id) {
        return new Book();
    }

}
