package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public Level level() {
        return Level.HIGH;
    }

    public String describe(Filter filter) {
        return filter.getText();
    }

}
