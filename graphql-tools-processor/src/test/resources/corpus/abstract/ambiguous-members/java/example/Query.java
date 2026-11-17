package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public Wrapped wrapped() {
        return new OnlyBoxed();
    }

}
