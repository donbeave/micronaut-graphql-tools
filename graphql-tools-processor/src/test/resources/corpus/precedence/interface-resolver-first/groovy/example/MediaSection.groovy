package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class MediaSection implements Section {

    String title = 'media'

}
