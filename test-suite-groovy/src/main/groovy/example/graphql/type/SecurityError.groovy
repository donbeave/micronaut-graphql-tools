package example.graphql.type

import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLType

@CompileStatic
@GraphQLType
class SecurityError implements Error {
    final String message
    final SecurityErrorCode code

    SecurityError(String message, SecurityErrorCode code) {
        this.message = message
        this.code = code
    }
}
