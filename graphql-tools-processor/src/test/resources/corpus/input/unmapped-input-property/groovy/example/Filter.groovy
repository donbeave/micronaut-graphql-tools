package example

import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class Filter {

    String text
    String extra

}
