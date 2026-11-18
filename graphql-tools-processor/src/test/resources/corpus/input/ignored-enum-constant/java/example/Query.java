package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public Color color() {
        return Color.RED;
    }

}
