package example

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Parcel)
class ParcelResolver {

    @GraphQLSchemaCoordinate("Parcel.class")
    String kind(Parcel parcel) {
        return 'parcel'
    }

}
