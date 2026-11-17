package example.graphql.type;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class ValidationError implements Error {

    private final String message;
    private final ValidationErrorCode code;

    public ValidationError(String message, ValidationErrorCode code) {
        this.message = message;
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public ValidationErrorCode getCode() {
        return code;
    }

}
