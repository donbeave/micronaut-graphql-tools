package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "MediaSection")
final class MediaSection {

    String getTitle() {
        return "title"
    }

}
