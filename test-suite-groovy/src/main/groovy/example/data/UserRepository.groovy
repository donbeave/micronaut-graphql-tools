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

import groovy.transform.CompileStatic
import jakarta.inject.Singleton

@CompileStatic
@Singleton
class UserRepository {

    static class UsernameAlreadyExistsException extends RuntimeException {
    }

    private static long lastId = 0L
    private static final List<User> STATE = [
            new User(id: ++lastId, username: 'test', firstName: 'Test', lastName: 'Test', password: 'test'),
            new User(id: ++lastId, username: 'bob', firstName: 'Bob', lastName: 'Di', password: 'bodi'),
            new User(id: ++lastId, username: 'travis', firstName: 'Travis', lastName: 'Mot', password: 'trav'),
    ]

    List<User> search(String contains) {
        STATE.findAll {
            it.username.contains(contains) || it.firstName.contains(contains) || it.lastName.contains(contains)
        }
    }

    User findByUsername(String username) {
        STATE.find { it.username == username }
    }

    void create(User user) {
        if (findByUsername(user.username) != null) {
            throw new UsernameAlreadyExistsException()
        }
        user.id = ++lastId
        STATE.add(user)
    }
}
