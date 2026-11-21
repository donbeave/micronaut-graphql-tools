package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "TextSection")
open class TextSection : Section {

    val title: String = "title"

}
