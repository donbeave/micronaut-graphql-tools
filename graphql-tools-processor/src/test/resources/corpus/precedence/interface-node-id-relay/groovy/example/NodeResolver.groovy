package example

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Node)
class NodeResolver {

    @GraphQLSchemaCoordinate('Node.id')
    String id(Node node) {
        return 'global'
    }

}
