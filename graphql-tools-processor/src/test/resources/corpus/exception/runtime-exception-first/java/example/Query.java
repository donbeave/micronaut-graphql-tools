package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLRootResolver
public class Query {

    public Query() {
        throw new IllegalStateException("no database");
    }

    public String username(String prefix, int extra) {
        return prefix;
    }

}
