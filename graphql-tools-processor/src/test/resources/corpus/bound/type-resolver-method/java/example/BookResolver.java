package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLTypeResolver(Book.class)
public class BookResolver {

    public String summary(Book book) {
        return "Summary of " + book.getTitle();
    }

}
