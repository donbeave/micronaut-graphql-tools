package example

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Parcel::class)
class ParcelResolver {

    @GraphQLSchemaCoordinate("Parcel.class")
    fun kind(parcel: Parcel): String = "parcel"

}
