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
