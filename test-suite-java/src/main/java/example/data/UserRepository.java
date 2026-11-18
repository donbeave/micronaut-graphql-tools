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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class UserRepository {

    public static class UsernameAlreadyExistsException extends RuntimeException {
    }

    private static final List<User> STATE = new ArrayList<>();

    private static Long lastId = 0L;

    static {
        STATE.add(
                new User()
                        .setId(++lastId)
                        .setUsername("test")
                        .setFirstName("Test")
                        .setLastName("Test")
                        .setPassword("test")
        );
        STATE.add(
                new User()
                        .setId(++lastId)
                        .setUsername("bob")
                        .setFirstName("Bob")
                        .setLastName("Di")
                        .setPassword("bodi")
        );
        STATE.add(
                new User()
                        .setId(++lastId)
                        .setUsername("travis")
                        .setFirstName("Travis")
                        .setLastName("Mot")
                        .setPassword("trav")
        );
    }

    public List<User> search(String contains) {
        return STATE.stream()
                .filter(filterByString(contains))
                .collect(Collectors.toList());
    }

    public Optional<User> findByUsername(String username) {
        return STATE.stream()
                .filter(it -> it.getUsername().equals(username))
                .findFirst();
    }

    public void create(@NonNull User user) {
        ArgumentUtils.requireNonNull("user", user);

        if (!findByUsername(user.getUsername()).isEmpty()) {
            throw new UsernameAlreadyExistsException();
        }

        user.setId(++lastId);

        STATE.add(user);
    }

    private Predicate<? super User> filterByString(String contains) {
        return it -> it.getUsername().contains(contains)
                || it.getFirstName().contains(contains)
                || it.getLastName().contains(contains);
    }

}
