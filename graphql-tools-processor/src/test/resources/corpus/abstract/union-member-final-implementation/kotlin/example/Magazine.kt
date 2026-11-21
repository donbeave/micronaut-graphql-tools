package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Magazine")
class Magazine : SearchResult {

    val issue: Int = 1

}
