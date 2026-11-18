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
class BookRepository {

    private static final List<Book> STATE = [
            new Book('ab', 'The Fractured Sage', 1982),
            new Book('ax', 'Considered for Duty', 2006),
            new Book('gd', 'Sword of Six', 1975),
            new Book('we', 'The Dark Boy', 2035),
            new Book('df', 'Strike of Arrakis', 2000),
    ]

    List<Book> findAll() {
        STATE
    }

    Book findById(String id) {
        STATE.find { it.id == id }
    }

    void save(Book book) {
        STATE.add(book)
    }
}
