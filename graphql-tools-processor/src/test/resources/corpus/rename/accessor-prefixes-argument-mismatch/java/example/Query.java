package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class Query {

    public List<String> getBooks(int limit) {
        return List.of();
    }

    public String getVersion() {
        return "1";
    }

}
