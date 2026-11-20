package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Parcel::class)
class ParcelResolver {

    fun author(parcel: Parcel): String = "Jane"

    fun getAuthor(parcel: Parcel): String = "John"

    fun isActive(parcel: Parcel): Boolean = true

}
