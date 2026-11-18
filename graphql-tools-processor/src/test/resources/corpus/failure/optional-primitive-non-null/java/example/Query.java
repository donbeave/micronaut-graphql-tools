package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.OptionalInt;

@GraphQLRootResolver
public class Query {

    public OptionalInt count() {
        return OptionalInt.of(3);
    }

}
