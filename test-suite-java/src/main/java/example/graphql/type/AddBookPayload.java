package example.graphql.type;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class AddBookPayload {

    private final Error error;

    public AddBookPayload() {
        this.error = null;
    }

    public AddBookPayload(Error error) {
        this.error = error;
    }

    public Error getError() {
        return error;
    }

}
