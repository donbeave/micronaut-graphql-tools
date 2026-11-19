package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class QueryResolver {

    public String ping() {
        return "pong";
    }

}
