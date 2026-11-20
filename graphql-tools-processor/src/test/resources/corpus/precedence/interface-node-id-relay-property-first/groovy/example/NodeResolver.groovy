package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Node)
class NodeResolver {

    @GraphQLSchemaCoordinate('Node.id')
    String id(Node node) {
        return 'global'
    }

}
