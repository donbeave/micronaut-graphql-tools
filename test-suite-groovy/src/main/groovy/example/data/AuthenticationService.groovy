package example.data

import groovy.transform.CompileStatic
import jakarta.inject.Singleton

@CompileStatic
@Singleton
class AuthenticationService {

    static class UserNotFoundException extends RuntimeException {
    }

    static class IncorrectCredentialsException extends RuntimeException {
    }

    private final UserRepository userRepository
    private User currentUser

    AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository
    }

    User login(String username, String password) {
        User user = userRepository.findByUsername(username)
        if (user == null) {
            throw new UserNotFoundException()
        }
        if (user.password != password) {
            throw new IncorrectCredentialsException()
        }
        currentUser = user
        return user
    }

    void logout() {
        currentUser = null
    }

    User getCurrentUser() {
        currentUser
    }
}
