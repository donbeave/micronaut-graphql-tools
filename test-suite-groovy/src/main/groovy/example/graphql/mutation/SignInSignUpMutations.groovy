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
import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@CompileStatic
@GraphQLRootResolver
class SignInSignUpMutations {

    private final AuthenticationService authenticationService
    private final UserRepository userRepository

    SignInSignUpMutations(AuthenticationService authenticationService, UserRepository userRepository) {
        this.authenticationService = authenticationService
        this.userRepository = userRepository
    }

    @ElapsedTime
    SignInPayload signIn(String username, String password) {
        try {
            User user = authenticationService.login(username, password)
            return new SignInPayload(user)
        } catch (AuthenticationService.UserNotFoundException ignored) {
            return new SignInPayload(new ValidationError('User ' + username + ' not found.', ValidationErrorCode.NOT_FOUND))
        } catch (AuthenticationService.IncorrectCredentialsException ignored) {
            return new SignInPayload(new SecurityError('Password is incorrect.', SecurityErrorCode.INCORRECT_CREDENTIALS))
        }
    }

    @ElapsedTime
    SignUpPayload signUp(SignUpInput input) {
        try {
            User user = new User(username: input.username, password: input.password,
                    firstName: input.firstName, lastName: input.lastName)
            userRepository.create(user)
            return new SignUpPayload(user)
        } catch (UserRepository.UsernameAlreadyExistsException ignored) {
            return new SignUpPayload(new ValidationError('User with username ' + input.username + ' already exists.',
                    ValidationErrorCode.DUPLICATE))
        }
    }
}
