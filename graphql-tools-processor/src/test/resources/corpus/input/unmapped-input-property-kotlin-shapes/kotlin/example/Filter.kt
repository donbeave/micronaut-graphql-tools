package example

import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class Filter(var text: String, val fromConstructorVal: String, var fromConstructorVar: String) {

    var fromBody: String = ""

    val readOnly: String = "fixed"

    val computed: String
        get() = text

}
