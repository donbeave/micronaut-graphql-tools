package example.graphql.type

import example.data.User
import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLType

@CompileStatic
@GraphQLType
class Profile {
    private final User user

    Profile(User user) {
        this.user = user
    }

    String getUsername() {
        user.username
    }

    String getFullName() {
        user.firstName + ' ' + user.lastName
    }
}
