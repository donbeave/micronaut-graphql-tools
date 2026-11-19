package example

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Parcel)
class ParcelResolver {

    String getAuthor(Parcel parcel) {
        return 'Jane'
    }

    boolean isActive(Parcel parcel) {
        return true
    }

}
