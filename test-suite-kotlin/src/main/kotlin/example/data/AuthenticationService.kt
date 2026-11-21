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
package example.data

import jakarta.inject.Singleton

/**
 * Checks credentials and finds users. It holds no state about who is logged in: the user of a request comes from the
 * request, see `CurrentUserContributor`, because a singleton shared by all requests cannot hold a per-request value.
 */
@Singleton
class AuthenticationService(private val userRepository: UserRepository) {

    class UserNotFoundException : RuntimeException()

    class IncorrectCredentialsException : RuntimeException()

    fun login(username: String, password: String): User {
        val user = userRepository.findByUsername(username) ?: throw UserNotFoundException()
        if (user.password != password) {
            throw IncorrectCredentialsException()
        }
        return user
    }

    fun find(username: String): User? = userRepository.findByUsername(username)

    companion object {
        const val CURRENT_USER = "example.currentUser"
    }
}
