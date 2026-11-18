package example.graphql.type

import io.micronaut.core.annotation.Introspected

@Introspected
enum ValidationErrorCode {
    NOT_FOUND,
    DUPLICATE
}
