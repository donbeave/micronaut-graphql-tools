package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class OtherQuery {

    public String other(Extra input) {
        return input.getExtra();
    }

}
