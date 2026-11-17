package example.graphql.input

import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class SignUpInput {
    lateinit var username: String
    lateinit var password: String
    lateinit var firstName: String
    lateinit var lastName: String
}
