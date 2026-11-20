package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.time.LocalDate;
import java.util.List;

@Singleton
@GraphQLRootResolver
public class SearchQuery {

    public List<SearchResult> search() {
        return List.of();
    }

    public LocalDate today() {
        return null;
    }

}
