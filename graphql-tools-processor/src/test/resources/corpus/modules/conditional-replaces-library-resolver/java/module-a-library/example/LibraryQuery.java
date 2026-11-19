package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class LibraryQuery {

    public String hello() {
        return "library";
    }

}
