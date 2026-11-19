package example

import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Parcel {

    val id: String = "1"

    @field:GraphQLField("headline")
    val title: String = "Fragile"

}
