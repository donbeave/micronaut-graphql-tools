package example;

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Node.class)
public class NodeResolver {

    @GraphQLSchemaCoordinate("Node.id")
    public String id(Node node) {
        return "global";
    }

}
