package example

import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class Filter {

    var text: String = ""
    @GraphQLIgnore
    var extra: String = ""

}
