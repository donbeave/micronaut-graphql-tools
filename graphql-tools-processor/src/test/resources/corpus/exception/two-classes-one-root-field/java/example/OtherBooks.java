package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class OtherBooks {

    public String book(String id, int extra) {
        return id;
    }

}
