package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

import java.util.Optional;

@Singleton
@GraphQLTypeResolver(Novel.class)
public class BookResolver {

    @GraphQLSchemaCoordinate("Book.author")
    public Optional<String> author(Novel book) {
        return Optional.empty();
    }

}
