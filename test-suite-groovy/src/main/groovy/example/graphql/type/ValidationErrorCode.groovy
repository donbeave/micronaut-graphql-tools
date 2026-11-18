package example.graphql.type

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected

@CompileStatic
@Introspected
enum ValidationErrorCode {
    NOT_FOUND,
    DUPLICATE
}
