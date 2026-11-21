package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "MediaSection")
class MediaSection {

    val title: String = "title"

}
