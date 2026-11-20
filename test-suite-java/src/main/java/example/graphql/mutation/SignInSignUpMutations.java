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
package example.graphql.mutation;

import jakarta.inject.Singleton;
import example.ElapsedTime;
import example.data.AuthenticationService;
import example.data.User;
import example.data.UserRepository;
import example.graphql.input.SignUpInput;
import example.graphql.type.SecurityError;
import example.graphql.type.SecurityErrorCode;
import example.graphql.type.SignInPayload;
import example.graphql.type.SignUpPayload;
import example.graphql.type.ValidationError;
import example.graphql.type.ValidationErrorCode;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class SignInSignUpMutations {

    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    public SignInSignUpMutations(AuthenticationService authenticationService, UserRepository userRepository) {
        this.authenticationService = authenticationService;
        this.userRepository = userRepository;
    }

    @ElapsedTime
    public SignInPayload signIn(String username, String password) {
        try {
            User user = authenticationService.login(username, password);

            return new SignInPayload(user);
        } catch (AuthenticationService.UserNotFoundException e) {
            String errorMessage = "User " + username + " not found.";
            return new SignInPayload(new ValidationError(errorMessage, ValidationErrorCode.NOT_FOUND));
        } catch (AuthenticationService.IncorrectCredentialsException e) {
            return new SignInPayload(new SecurityError("Password is incorrect.",
                    SecurityErrorCode.INCORRECT_CREDENTIALS));
        }
    }

    @ElapsedTime
    public SignUpPayload signUp(SignUpInput input) {
        try {
            User user = new User();
            user.setUsername(input.getUsername());
            user.setPassword(input.getPassword());
            user.setFirstName(input.getFirstName());
            user.setLastName(input.getLastName());

            userRepository.create(user);

            return new SignUpPayload(user);
        } catch (UserRepository.UsernameAlreadyExistsException e) {
            String errorMessage = "User with username " + input.getUsername() + " already exists.";
            return new SignUpPayload(new ValidationError(errorMessage, ValidationErrorCode.DUPLICATE));
        }
    }

}
