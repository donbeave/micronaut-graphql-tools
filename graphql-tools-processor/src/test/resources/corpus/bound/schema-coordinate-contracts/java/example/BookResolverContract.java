package example;

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Book.class)
public interface BookResolverContract {

    @GraphQLSchemaCoordinate("Book.summary")
    String summary(Book book);

}
