package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class OtherQuery {

    public String other(Extra input) {
        return input.getExtra();
    }

}
