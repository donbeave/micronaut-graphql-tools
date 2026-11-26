package example

import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Slip {

    @GraphQLField("title")
    fun first(): String = "first"

    @GraphQLField("title")
    fun second(): String = "second"

}
