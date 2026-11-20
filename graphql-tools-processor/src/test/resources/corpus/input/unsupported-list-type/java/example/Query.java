package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.ArrayDeque;

@Singleton
@GraphQLRootResolver
public class Query {

    public String names(ArrayDeque<String> values) {
        return String.join(",", values);
    }

}
