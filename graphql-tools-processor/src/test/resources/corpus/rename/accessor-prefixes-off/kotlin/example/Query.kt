package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    fun getVersion(): String = "1"

    fun parcel(): Parcel = Parcel()

}
