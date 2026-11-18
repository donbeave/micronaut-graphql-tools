package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class User {

    private final String id;

    public User(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

}
