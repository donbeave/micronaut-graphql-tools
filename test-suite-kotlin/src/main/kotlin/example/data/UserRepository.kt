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
class UserRepository {

    class UsernameAlreadyExistsException : RuntimeException()

    fun search(contains: String): List<User> = STATE.filter {
        it.username!!.contains(contains) || it.firstName!!.contains(contains) || it.lastName!!.contains(contains)
    }

    fun findByUsername(username: String): User? = STATE.firstOrNull { it.username == username }

    fun create(user: User) {
        if (findByUsername(user.username!!) != null) {
            throw UsernameAlreadyExistsException()
        }
        user.id = ++lastId
        STATE.add(user)
    }

    companion object {
        private var lastId = 0L
        private val STATE = mutableListOf(
            User(++lastId, "test", "Test", "Test", "test"),
            User(++lastId, "bob", "Bob", "Di", "bodi"),
            User(++lastId, "travis", "Travis", "Mot", "trav"),
        )
    }
}
