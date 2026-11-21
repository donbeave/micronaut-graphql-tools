package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class Query {

    public List<SearchResult> search() {
        return List.of();
    }

}
