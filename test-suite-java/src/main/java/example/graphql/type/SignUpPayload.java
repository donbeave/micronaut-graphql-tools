package example.graphql.type;

import example.data.User;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class SignUpPayload {

    private final Error error;
    private final User data;

    public SignUpPayload(Error error) {
        this.error = error;
        this.data = null;
    }

    public SignUpPayload(User data) {
        this.error = null;
        this.data = data;
    }

    public Error getError() {
        return error;
    }

    public User getData() {
        return data;
    }

}
