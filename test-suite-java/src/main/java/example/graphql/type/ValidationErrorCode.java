package example.graphql.type;

import io.micronaut.core.annotation.Introspected;

@Introspected
public enum ValidationErrorCode {

    NOT_FOUND,
    DUPLICATE

}
