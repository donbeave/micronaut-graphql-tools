package example

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

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
