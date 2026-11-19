package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

import java.util.List;

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
