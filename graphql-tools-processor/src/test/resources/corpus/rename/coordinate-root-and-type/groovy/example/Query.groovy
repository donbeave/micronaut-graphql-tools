package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate

@Singleton
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
