package example;

@io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(Book.class)
public class BookResolver extends BaseResolver<Book> {
}
