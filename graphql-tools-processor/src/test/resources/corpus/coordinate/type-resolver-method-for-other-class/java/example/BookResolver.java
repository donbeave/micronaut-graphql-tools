package example;

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

import java.util.Optional;

@GraphQLTypeResolver(Novel.class)
public class BookResolver {

    @GraphQLSchemaCoordinate("Book.author")
    public Optional<String> author(Novel book) {
        return Optional.empty();
    }

}
