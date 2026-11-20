package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

import java.util.Optional;

@Singleton
@GraphQLTypeResolver(Book.class)
public class OtherBookResolver {

    public Optional<String> author(Book book) {
        return Optional.empty();
    }

}
