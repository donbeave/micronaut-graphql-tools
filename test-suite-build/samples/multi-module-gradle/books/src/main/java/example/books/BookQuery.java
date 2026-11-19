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
package example.books;

import example.model.Book;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class BookQuery {

    public Book book(String id) {
        return new Book(id, "Title " + id);
    }

    public List<Book> books(Integer limit) {
        return List.of(new Book("1", "First"));
    }

}
