package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Parcel)
class ParcelResolver {

    String author(Parcel parcel) {
        return 'Jane'
    }

    String getAuthor(Parcel parcel) {
        return 'John'
    }

    boolean isActive(Parcel parcel) {
        return true
    }

}
