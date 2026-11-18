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
package example.graphql.query

import example.ElapsedTime
import example.data.Book
import example.data.BookRepository
import io.micronaut.context.event.StartupEvent
import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.runtime.event.annotation.EventListener

// tag::resolver[]
@GraphQLRootResolver
open class AllBooksQuery(private val bookRepository: BookRepository) {

    @ElapsedTime
    open fun allBooks(): List<Book> = bookRepository.findAll()

    @EventListener
    open fun onStartup(event: StartupEvent) {
    }

    @GraphQLIgnore
    open fun helper(value: String): String = value
}
// end::resolver[]
