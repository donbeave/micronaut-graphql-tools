package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLArgument;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Query {

    public String size(@GraphQLArgument("short") Integer width, @GraphQLArgument("long") Integer length) {
        return "long=" + length + " short=" + width;
    }

}
