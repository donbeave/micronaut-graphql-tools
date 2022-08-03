package example.graphql.type;

import example.data.User;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Profile {

    private final User user;

    public Profile(User user) {
        this.user = user;
    }

    public String getUsername() {
        return user.getUsername();
    }

    public String getFullName() {
        return user.getFirstName() + " " + user.getLastName();
    }

}
