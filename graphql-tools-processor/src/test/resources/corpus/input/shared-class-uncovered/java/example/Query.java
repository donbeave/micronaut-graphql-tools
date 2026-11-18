package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public String create(Dto input) {
        return input.getName();
    }

    public String rename(Dto input) {
        return input.getName();
    }

}

