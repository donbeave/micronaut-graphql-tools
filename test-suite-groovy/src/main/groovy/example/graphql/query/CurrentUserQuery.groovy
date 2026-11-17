package example.graphql.query

import example.ElapsedTime
import example.data.AuthenticationService
import example.data.User
import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@CompileStatic
@GraphQLRootResolver
class CurrentUserQuery {

    private final AuthenticationService authenticationService

    CurrentUserQuery(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService
    }

    @ElapsedTime
    User currentUser() {
        authenticationService.currentUser
    }
}
