package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class Query {

    public List<String> getBooks(int limit) {
        return List.of();
    }

    public String getVersion() {
        return "1";
    }

}
