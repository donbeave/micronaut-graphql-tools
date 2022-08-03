package example.graphql.type;

import example.data.User;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class SignInPayload {

    private final Error error;
    private final User data;

    public SignInPayload(Error error) {
        this.error = error;
        this.data = null;
    }

    public SignInPayload(User data) {
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
