package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "TextSection")
class TextSection implements Section {

    String getTitle() {
        return "title"
    }

}
