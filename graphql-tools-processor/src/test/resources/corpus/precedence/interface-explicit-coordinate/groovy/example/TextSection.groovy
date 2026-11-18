package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class TextSection implements Section {

    String title = 'text'
    String summary = 'own'

}
