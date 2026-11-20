package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Query {

    public Level level() {
        return Level.HIGH;
    }

    public String describe(Filter filter) {
        return filter.getText();
    }

}
