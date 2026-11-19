package example.model

import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class BookFilter {

    String title

}
