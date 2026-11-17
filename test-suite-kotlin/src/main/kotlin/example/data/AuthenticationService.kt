package example.data

import jakarta.inject.Singleton

@Singleton
class AuthenticationService(private val userRepository: UserRepository) {

    class UserNotFoundException : RuntimeException()

    class IncorrectCredentialsException : RuntimeException()

    var currentUser: User? = null
        private set

    fun login(username: String, password: String): User {
        val user = userRepository.findByUsername(username) ?: throw UserNotFoundException()
        if (user.password != password) {
            throw IncorrectCredentialsException()
        }
        currentUser = user
        return user
    }

    fun logout() {
        currentUser = null
    }
}
