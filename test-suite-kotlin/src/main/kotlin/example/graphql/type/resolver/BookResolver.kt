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
package example.graphql.type.resolver

import jakarta.inject.Singleton
import example.ElapsedTime
import example.data.Book
import example.data.BookSeller
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.dataloader.DataLoader
import java.util.concurrent.CompletionStage

@Singleton
@GraphQLTypeResolver(Book::class)
open class BookResolver {

    @ElapsedTime
    open fun sellers(book: Book, dataLoader: DataLoader<String, List<BookSeller>>): CompletionStage<List<BookSeller>> {
        return dataLoader.load(book.id)
    }
}
