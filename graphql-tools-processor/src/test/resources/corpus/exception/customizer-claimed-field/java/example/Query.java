package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Query {

    public String hello() {
        return "world";
    }

    public String version(int build) {
        return "1.0";
    }

}
