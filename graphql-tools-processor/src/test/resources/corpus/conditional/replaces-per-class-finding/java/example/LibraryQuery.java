package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class LibraryQuery {

    public int count() {
        return 1;
    }

}
