package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate

@GraphQLRootResolver
class Query {

    @GraphQLSchemaCoordinate("Query.books")
    List<String> fetch(int limit) {
        return []
    }

    @GraphQLSchemaCoordinate("Query.version")
    String release() {
        return '1'
    }

}
