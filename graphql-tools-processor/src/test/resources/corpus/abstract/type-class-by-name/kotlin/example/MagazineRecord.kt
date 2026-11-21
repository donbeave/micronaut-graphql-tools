package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Magazine")
class MagazineRecord : SearchResult {

    val issue: Int = 1

}
