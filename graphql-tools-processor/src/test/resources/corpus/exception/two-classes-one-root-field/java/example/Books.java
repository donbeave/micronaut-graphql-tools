package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Books {

    public String book(String id) {
        return id;
    }

}
