package example

import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Parcel {

    val id: String = "1"

    @GraphQLField("class")
    fun kind(): String = "parcel"

    @GraphQLField("hashCode")
    fun digest(): Int = 42

}
