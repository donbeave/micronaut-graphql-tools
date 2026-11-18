package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Others {

    public String other(String id, int extra) {
        return id;
    }

}
