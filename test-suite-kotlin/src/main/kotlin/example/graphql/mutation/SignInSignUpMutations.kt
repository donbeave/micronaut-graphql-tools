/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
