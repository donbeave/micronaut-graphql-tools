package example

import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class Extra {

    var id: Int = 0
    var extra: String = ""

}
