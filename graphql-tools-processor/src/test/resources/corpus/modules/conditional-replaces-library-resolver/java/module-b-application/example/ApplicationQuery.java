package example;

import jakarta.inject.Singleton;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
@Replaces(LibraryQuery.class)
public class ApplicationQuery {

    public String hello() {
        return "application";
    }

}
