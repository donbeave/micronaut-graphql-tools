package example;

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

import java.util.Optional;

@GraphQLTypeResolver(Book.class)
public class BookResolver {

    @GraphQLSchemaCoordinate("Book.title")
    public String title(Book book) {
        return null;
    }

    @GraphQLSchemaCoordinate("Book.author")
    public Optional<String> author(Book book) {
        return Optional.empty();
    }

}
