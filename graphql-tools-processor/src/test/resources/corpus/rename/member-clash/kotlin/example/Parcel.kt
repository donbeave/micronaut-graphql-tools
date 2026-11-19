package example

import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Parcel {

    val id: String = "1"

    val title: String = "Fragile"

    @GraphQLField("title")
    fun heading(): String = "Handle with care"

}
