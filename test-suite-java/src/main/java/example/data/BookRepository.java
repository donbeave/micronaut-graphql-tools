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

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
public class BookRepository {

    private static final List<Book> STATE = new ArrayList<>();

    static {
        STATE.add(new Book("ab", "The Fractured Sage", 1982));
        STATE.add(new Book("ax", "Considered for Duty", 2006));
        STATE.add(new Book("gd", "Sword of Six", 1975));
        STATE.add(new Book("we", "The Dark Boy", 2035));
        STATE.add(new Book("df", "Strike of Arrakis", 2000));
    }

    public List<Book> findAll() {
        return STATE;
    }

    public Optional<Book> findById(String id) {
        return STATE.stream()
                .filter(it -> it.getId().equals(id))
                .findFirst();
    }

    public void save(Book book) {
        STATE.add(book);
    }

}
