package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public String paint(Color color) {
        return color.name();
    }

}
