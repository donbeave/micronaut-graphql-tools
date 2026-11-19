package example;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
@Replaces(LibraryQuery.class)
public class ApplicationQuery {

    public String hello() {
        return "application";
    }

}
