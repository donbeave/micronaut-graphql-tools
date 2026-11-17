package example.graphql.input

import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLInput

@CompileStatic
@GraphQLInput
class SignUpInput {
    String username
    String password
    String firstName
    String lastName
}
