package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

@Singleton
@GraphQLRootResolver
public class Query {

    public OptionalInt count() {
        return OptionalInt.of(3);
    }

    public OptionalLong big() {
        return OptionalLong.empty();
    }

    public OptionalDouble ratio() {
        return OptionalDouble.of(1.5d);
    }

    public List<OptionalInt> counts() {
        return List.of(OptionalInt.of(1), OptionalInt.empty());
    }

}
