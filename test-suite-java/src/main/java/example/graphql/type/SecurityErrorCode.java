package example.graphql.type;

import io.micronaut.core.annotation.Introspected;

@Introspected
public enum SecurityErrorCode {

    INCORRECT_CREDENTIALS,
    NOT_AUTHENTICATED

}
