package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Magazine")
class MagazineGraph {

    val issue: Int = 1

}
