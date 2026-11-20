package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@Singleton
@GraphQLTypeResolver(Node.class)
public class NodeResolver {

    @GraphQLSchemaCoordinate("Node.id")
    public String id(Node node) {
        return "global";
    }

}
