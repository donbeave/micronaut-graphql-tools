package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate

@GraphQLRootResolver
class Query {

    @GraphQLSchemaCoordinate("Query.default")
    fun defaultValue(): String = "plain"

    @GraphQLSchemaCoordinate("Query.parcel")
    fun fetchParcel(): Parcel = Parcel()

}
