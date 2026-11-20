package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Others {

    public String other(String id, int extra) {
        return id;
    }

}
