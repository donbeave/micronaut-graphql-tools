package example;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.OptionalInt;

@GraphQLRootResolver
@Replaces(LibraryQuery.class)
public class ApplicationQuery {

    public OptionalInt count() {
        return OptionalInt.of(3);
    }

}
