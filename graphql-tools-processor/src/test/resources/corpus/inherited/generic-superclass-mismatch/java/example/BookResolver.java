package example;

import jakarta.inject.Singleton;

@io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(Book.class)
@Singleton
public class BookResolver extends BaseResolver<Book> {
}
