package example.graphql.query;

import example.ElapsedTime;
import example.data.AuthenticationService;
import example.data.User;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class CurrentUserQuery {

    private final AuthenticationService authenticationService;

    public CurrentUserQuery(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @ElapsedTime
    public User currentUser() {
        return authenticationService.getCurrentUser().orElse(null);
    }

}
