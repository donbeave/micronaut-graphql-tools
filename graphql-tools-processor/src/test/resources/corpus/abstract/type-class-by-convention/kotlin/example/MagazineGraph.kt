package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class MagazineGraph : SearchResult {

    val issue: Int = 1

}
