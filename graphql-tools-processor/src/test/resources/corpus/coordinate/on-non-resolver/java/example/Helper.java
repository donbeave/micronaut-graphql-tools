package example;

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import jakarta.inject.Singleton;

@Singleton
public class Helper {

    @GraphQLSchemaCoordinate("Query.second")
    public String second() {
        return "2";
    }

}
