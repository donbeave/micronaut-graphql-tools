package example.graphql.type;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class SecurityError implements Error {

    private final String message;
    private final SecurityErrorCode code;

    public SecurityError(String message, SecurityErrorCode code) {
        this.message = message;
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public SecurityErrorCode getCode() {
        return code;
    }

}
