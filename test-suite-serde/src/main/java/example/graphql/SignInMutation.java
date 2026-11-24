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
package example.graphql;

import example.User;
import example.UserRepository;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
@GraphQLRootResolver
public class SignInMutation {

    private final UserRepository userRepository;

    public SignInMutation(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public SignInPayload signIn(String username, String password) {
        Optional<User> user = userRepository.find(username);
        if (user.isEmpty()) {
            return new SignInPayload(new ValidationError("User " + username + " not found."), null);
        }
        if (!user.get().password().equals(password)) {
            return new SignInPayload(new ValidationError("Password is incorrect."), null);
        }
        return new SignInPayload(null, user.get());
    }

}
