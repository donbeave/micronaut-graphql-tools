package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class User {

    public String getUsername() {
        return "abc";
    }

}
