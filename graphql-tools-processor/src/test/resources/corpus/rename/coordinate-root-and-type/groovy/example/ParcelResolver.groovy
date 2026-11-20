package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Parcel)
class ParcelResolver {

    @GraphQLSchemaCoordinate("Parcel.class")
    String kind(Parcel parcel) {
        return 'parcel'
    }

}
