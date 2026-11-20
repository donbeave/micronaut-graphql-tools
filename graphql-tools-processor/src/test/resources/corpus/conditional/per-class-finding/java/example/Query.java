package example;

import jakarta.inject.Singleton;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.OptionalInt;

@Singleton
@GraphQLRootResolver
@Requires(env = "prod")
public class Query {

    public OptionalInt count() {
        return OptionalInt.of(3);
    }

}
