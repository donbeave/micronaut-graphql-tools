package example;

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLTypeResolver(Book.class)
public class BookResolver {

    @GraphQLSchemaCoordinate("Book.summary")
    public String summary(Book book) {
        return "Summary of " + book.getTitle();
    }

}
