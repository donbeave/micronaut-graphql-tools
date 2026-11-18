package example

import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class Dto {

    var name: String = ""
    var id: Int = 0

}
