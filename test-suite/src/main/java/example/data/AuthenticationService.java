package example.data;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class AuthenticationService {

    private final UserRepository userRepository;

    private User currentUser;

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

        currentUser = user;

        return currentUser;
    }

    public void logout() {
        this.currentUser = null;
    }

    public void setCurrentUser(@NonNull User user) {
        ArgumentUtils.requireNonNull("user", user);

        this.currentUser = user;
    }

    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public static class UserNotFoundException extends RuntimeException {
    }

    public static class IncorrectCredentialsException extends RuntimeException {
    }

}
