package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

import java.util.Optional;

@GraphQLTypeResolver(Book.class)
public class BookResolver {

    public String title(Book book) {
        return null;
    }

    public Optional<String> author(Book book) {
        return Optional.empty();
    }

}
