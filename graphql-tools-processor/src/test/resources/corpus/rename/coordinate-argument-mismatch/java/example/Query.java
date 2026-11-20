package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class Query {

    @GraphQLSchemaCoordinate("Query.books")
    public List<String> fetch(int limit) {
        return List.of();
    }

    @GraphQLSchemaCoordinate("Query.version")
    public String release() {
        return "1";
    }

}
