package example

import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Slip {

    @GraphQLIgnore
    @GraphQLField("title")
    fun heading(): String = "heading"

}
