package example.graphql.type

import io.micronaut.core.annotation.Introspected

@Introspected
enum class SecurityErrorCode {
    INCORRECT_CREDENTIALS,
    NOT_AUTHENTICATED,
}
