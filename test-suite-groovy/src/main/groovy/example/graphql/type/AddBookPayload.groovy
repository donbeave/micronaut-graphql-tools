package example.graphql.type

import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLType

@CompileStatic
@GraphQLType
class AddBookPayload {
    final Error error

    AddBookPayload() {
    }

    AddBookPayload(Error error) {
        this.error = error
    }
}
