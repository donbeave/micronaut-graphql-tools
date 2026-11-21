package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
open class MagazineRecord : SearchResult {

    val issue: Int = 1

}
