package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class MagazineModel : SearchResult {

    val issue: Int = 1

}
