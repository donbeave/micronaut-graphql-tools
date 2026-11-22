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
package example.data;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Checks credentials and finds users. It holds no state about who is logged in: the user of a request comes from the
 * request, see {@link example.graphql.CurrentUserContributor}, because a singleton shared by all requests cannot hold a
 * per-request value.
 */
@Singleton
public class AuthenticationService {

    public static final String CURRENT_USER = "example.currentUser";

    private final UserRepository userRepository;

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User login(@NonNull String username, @NonNull String password) {
        ArgumentUtils.requireNonNull("username", username);
        ArgumentUtils.requireNonNull("password", password);

        User user = userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);

        if (!user.getPassword().equals(password)) {
            throw new IncorrectCredentialsException();
        }

        return user;
    }

    public Optional<User> find(@NonNull String username) {
        ArgumentUtils.requireNonNull("username", username);

        return userRepository.findByUsername(username);
    }

    public static class UserNotFoundException extends RuntimeException {
    }

    public static class IncorrectCredentialsException extends RuntimeException {
    }

}
