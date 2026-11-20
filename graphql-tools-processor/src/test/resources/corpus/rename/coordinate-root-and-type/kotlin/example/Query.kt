package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate

@Singleton
@GraphQLRootResolver
class Query {

    @GraphQLSchemaCoordinate("Query.default")
    fun defaultValue(): String = "plain"

    @GraphQLSchemaCoordinate("Query.parcel")
    fun fetchParcel(): Parcel = Parcel()

}
