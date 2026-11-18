package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public String repeat(String text, int times) {
        return text.repeat(times);
    }

}
