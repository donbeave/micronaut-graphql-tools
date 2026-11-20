package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Query {

    public String describe(Filter filter, Color color) {
        return filter.getText();
    }

}
