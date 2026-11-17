package example.graphql.type

import example.data.User
import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLType

@CompileStatic
@GraphQLType
class SignInPayload {
    final Error error
    final User data

    SignInPayload(Error error) {
        this.error = error
    }

    SignInPayload(User data) {
        this.data = data
    }
}
