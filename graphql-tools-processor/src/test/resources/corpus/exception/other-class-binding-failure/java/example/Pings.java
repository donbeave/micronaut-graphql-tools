package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Pings {

    public String ping() {
        return "pong";
    }

    public String stray() {
        return "";
    }

}
