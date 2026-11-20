package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Parcel::class)
class ParcelResolver {

    fun getAuthor(parcel: Parcel): String = "Jane"

    fun isActive(parcel: Parcel): Boolean = true

}
