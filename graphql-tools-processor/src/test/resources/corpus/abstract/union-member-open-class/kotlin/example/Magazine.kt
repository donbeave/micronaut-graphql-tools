package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Magazine")
open class Magazine {

    val issue: Int = 1

}
