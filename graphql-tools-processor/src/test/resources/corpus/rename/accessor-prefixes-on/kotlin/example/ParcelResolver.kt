package example

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Parcel::class)
class ParcelResolver {

    fun getAuthor(parcel: Parcel): String = "Jane"

    fun isActive(parcel: Parcel): Boolean = true

}
