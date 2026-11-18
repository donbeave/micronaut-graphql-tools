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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class UserBookRepository {

    private static final Map<Long, List<Book>> STATE = new HashMap<>();

    public List<Book> findAllByUserId(@NonNull User user) {
        ArgumentUtils.requireNonNull("user", user);

        return STATE.getOrDefault(user.getId(), Collections.emptyList());
    }

    public void addBook(@NonNull User user, @NonNull Book book) {
        STATE.putIfAbsent(user.getId(), new ArrayList<>());
        if (STATE.get(user.getId()).contains(book)) {
            throw new DuplicateException();
        }
        STATE.get(user.getId()).add(book);
    }

    public static class DuplicateException extends RuntimeException {
    }

}
