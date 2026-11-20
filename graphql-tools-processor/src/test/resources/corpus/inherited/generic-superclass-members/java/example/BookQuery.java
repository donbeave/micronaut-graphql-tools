package example;

import jakarta.inject.Singleton;

@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
@Singleton
public class BookQuery extends BaseQuery<Book, String> {
}
