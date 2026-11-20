package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.OptionalInt;

@Singleton
@GraphQLRootResolver
public class Query {

    public OptionalInt count() {
        return OptionalInt.of(3);
    }

}
