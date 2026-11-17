package example.graphql.mutation

import example.ElapsedTime
import example.data.AuthenticationService
import example.data.User
import example.data.UserRepository
import example.graphql.input.SignUpInput
import example.graphql.type.SecurityError
import example.graphql.type.SecurityErrorCode
import example.graphql.type.SignInPayload
import example.graphql.type.SignUpPayload
import example.graphql.type.ValidationError
import example.graphql.type.ValidationErrorCode
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
open class SignInSignUpMutations(
    private val authenticationService: AuthenticationService,
    private val userRepository: UserRepository,
) {

    @ElapsedTime
    open fun signIn(username: String, password: String): SignInPayload = try {
        SignInPayload(authenticationService.login(username, password))
    } catch (e: AuthenticationService.UserNotFoundException) {
        SignInPayload(ValidationError("User $username not found.", ValidationErrorCode.NOT_FOUND))
    } catch (e: AuthenticationService.IncorrectCredentialsException) {
        SignInPayload(SecurityError("Password is incorrect.", SecurityErrorCode.INCORRECT_CREDENTIALS))
    }

    @ElapsedTime
    open fun signUp(input: SignUpInput): SignUpPayload = try {
        val user = User(
            username = input.username,
            password = input.password,
            firstName = input.firstName,
            lastName = input.lastName,
        )
        userRepository.create(user)
        SignUpPayload(user)
    } catch (e: UserRepository.UsernameAlreadyExistsException) {
        SignUpPayload(ValidationError("User with username ${input.username} already exists.", ValidationErrorCode.DUPLICATE))
    }
}
