package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public String describe(Filter filter, Color color) {
        return filter.getText();
    }

}
