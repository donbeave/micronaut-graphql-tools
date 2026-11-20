package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Query {

    public String create(Dto input) {
        return input.getName();
    }

    public String rename(Dto input) {
        return input.getName();
    }

}

