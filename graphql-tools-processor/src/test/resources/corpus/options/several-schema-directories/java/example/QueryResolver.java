package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class QueryResolver {

    public String stray() {
        return "";
    }

    public String ping() {
        return "pong";
    }

}
