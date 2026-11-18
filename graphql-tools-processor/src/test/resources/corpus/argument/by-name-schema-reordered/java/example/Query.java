package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public String page(Integer offset, Integer first) {
        return "first=" + first + " offset=" + offset;
    }

}
