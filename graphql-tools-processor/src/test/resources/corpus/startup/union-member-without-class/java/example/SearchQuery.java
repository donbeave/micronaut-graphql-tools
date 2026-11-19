package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.time.LocalDate;
import java.util.List;

@GraphQLRootResolver
public class SearchQuery {

    public List<SearchResult> search() {
        return List.of();
    }

    public LocalDate today() {
        return null;
    }

}
