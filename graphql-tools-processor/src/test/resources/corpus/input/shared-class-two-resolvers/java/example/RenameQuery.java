package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class RenameQuery {

    public String rename(Dto input) {
        return input.getName();
    }

}
