package example.graphql.type

import io.micronaut.core.annotation.Introspected

@Introspected
enum SecurityErrorCode {
    INCORRECT_CREDENTIALS,
    NOT_AUTHENTICATED
}
