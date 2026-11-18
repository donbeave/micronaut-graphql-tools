package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public String page(Integer first, Integer offset, Integer limit) {
        return "first=" + first + " offset=" + offset + " limit=" + limit;
    }

}
