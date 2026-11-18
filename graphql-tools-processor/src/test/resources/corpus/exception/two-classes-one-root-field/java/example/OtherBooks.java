package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class OtherBooks {

    public String book(String id, int extra) {
        return id;
    }

}
