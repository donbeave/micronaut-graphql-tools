package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

import java.util.Optional;

@GraphQLTypeResolver(Book.class)
public class OtherBookResolver {

    public Optional<String> author(Book book) {
        return Optional.empty();
    }

}
