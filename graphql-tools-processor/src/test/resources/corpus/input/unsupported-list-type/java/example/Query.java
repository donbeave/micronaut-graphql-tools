package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.ArrayDeque;

@GraphQLRootResolver
public class Query {

    public String names(ArrayDeque<String> values) {
        return String.join(",", values);
    }

}
