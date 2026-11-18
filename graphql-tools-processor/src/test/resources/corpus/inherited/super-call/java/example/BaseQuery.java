package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public abstract class BaseQuery {

    public String hello() {
        return "base";
    }

}
