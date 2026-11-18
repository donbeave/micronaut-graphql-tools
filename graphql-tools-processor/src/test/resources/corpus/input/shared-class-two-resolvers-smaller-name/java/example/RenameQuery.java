package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class RenameQuery {

    public String rename(Dto input) {
        return input.getName();
    }

}
