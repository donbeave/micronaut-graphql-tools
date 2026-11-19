package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate

@GraphQLRootResolver
class Query {

    @GraphQLSchemaCoordinate("Query.default")
    String defaultValue() {
        return 'plain'
    }

    @GraphQLSchemaCoordinate("Query.parcel")
    Parcel fetchParcel() {
        return new Parcel()
    }

}
