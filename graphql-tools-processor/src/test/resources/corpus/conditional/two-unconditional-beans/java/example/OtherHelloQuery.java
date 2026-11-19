package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class OtherHelloQuery {

    public String hello() {
        return "other";
    }

}
