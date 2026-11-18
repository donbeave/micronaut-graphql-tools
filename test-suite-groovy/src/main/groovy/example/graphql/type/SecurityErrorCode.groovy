package example.graphql.type

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected

@CompileStatic
@Introspected
enum SecurityErrorCode {
    INCORRECT_CREDENTIALS,
    NOT_AUTHENTICATED
}
