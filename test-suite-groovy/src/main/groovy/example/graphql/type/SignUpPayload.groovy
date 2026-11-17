package example.graphql.type

import example.data.User
import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLType

@CompileStatic
@GraphQLType
class SignUpPayload {
    final Error error
    final User data

    SignUpPayload(Error error) {
        this.error = error
    }

    SignUpPayload(User data) {
        this.data = data
    }
}
