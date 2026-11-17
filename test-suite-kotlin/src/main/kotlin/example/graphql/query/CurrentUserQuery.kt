package example.graphql.query

import example.ElapsedTime
import example.data.AuthenticationService
import example.data.User
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
open class CurrentUserQuery(private val authenticationService: AuthenticationService) {

    @ElapsedTime
    open fun currentUser(): User? = authenticationService.currentUser
}
