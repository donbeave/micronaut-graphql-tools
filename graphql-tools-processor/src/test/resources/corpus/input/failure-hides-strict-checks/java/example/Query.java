package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public Integer twice(int n) {
        return n * 2;
    }

    public String stray(String value) {
        return value;
    }

}
