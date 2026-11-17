package example.graphql.type

import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLType

@CompileStatic
@GraphQLType
class ValidationError implements Error {
    final String message
    final ValidationErrorCode code

    ValidationError(String message, ValidationErrorCode code) {
        this.message = message
        this.code = code
    }
}
