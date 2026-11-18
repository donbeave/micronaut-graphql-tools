package example.graphql.type

import io.micronaut.core.annotation.Introspected

@Introspected
enum class ValidationErrorCode {
    NOT_FOUND,
    DUPLICATE,
}
