package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

import java.util.Optional;

@GraphQLTypeResolver(Book.class)
public class BookResolver {

    public Optional<String> author(String book) {
        return Optional.empty();
    }

}
