package example

import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class Filter {

    var text: String = ""

}
