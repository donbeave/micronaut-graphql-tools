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
class BookRepository {

    fun findAll(): List<Book> = STATE

    fun findById(id: String): Book? = STATE.firstOrNull { it.id == id }

    fun save(book: Book) {
        STATE.add(book)
    }

    companion object {
        private val STATE = mutableListOf(
            Book("ab", "The Fractured Sage", 1982),
            Book("ax", "Considered for Duty", 2006),
            Book("gd", "Sword of Six", 1975),
            Book("we", "The Dark Boy", 2035),
            Book("df", "Strike of Arrakis", 2000),
        )
    }
}
