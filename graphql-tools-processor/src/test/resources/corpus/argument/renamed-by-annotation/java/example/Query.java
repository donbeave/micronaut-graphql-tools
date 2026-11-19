package example;

import io.micronaut.graphql.tools.annotation.GraphQLArgument;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public String size(@GraphQLArgument("short") Integer width, @GraphQLArgument("long") Integer length) {
        return "long=" + length + " short=" + width;
    }

}
