package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Parcel)
class ParcelResolver {

    String getAuthor(Parcel parcel) {
        return 'Jane'
    }

    boolean isActive(Parcel parcel) {
        return true
    }

}
