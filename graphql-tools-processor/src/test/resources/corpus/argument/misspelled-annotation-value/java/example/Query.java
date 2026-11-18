package example;

import io.micronaut.graphql.tools.annotation.GraphQLArgument;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public String page(@GraphQLArgument("frist") Integer first, Integer offset) {
        return "first=" + first + " offset=" + offset;
    }

}
