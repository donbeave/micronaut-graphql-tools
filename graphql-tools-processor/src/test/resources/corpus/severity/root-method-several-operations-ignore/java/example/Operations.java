package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Operations {

    public String ping() {
        return "pong";
    }

}
