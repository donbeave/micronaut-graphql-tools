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
package example.graphql.mutation;

import jakarta.inject.Singleton;
import example.ElapsedTime;
import example.data.AuthenticationService;
import example.data.Book;
import example.data.BookRepository;
import example.data.User;
import example.data.UserBookRepository;
import example.graphql.type.AddBookPayload;
import example.graphql.type.SecurityError;
import example.graphql.type.SecurityErrorCode;
import example.graphql.type.ValidationError;
import example.graphql.type.ValidationErrorCode;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.Optional;

@Singleton
@GraphQLRootResolver
public class AddBookMutation {

    private final BookRepository bookRepository;
    private final UserBookRepository userBookRepository;

    public AddBookMutation(BookRepository bookRepository, UserBookRepository userBookRepository) {
        this.bookRepository = bookRepository;
        this.userBookRepository = userBookRepository;
    }

    @ElapsedTime
    public AddBookPayload addBook(String id, DataFetchingEnvironment environment) {
        Optional<User> currentUser = Optional.ofNullable(environment.getGraphQlContext().get(AuthenticationService.CURRENT_USER));

        if (currentUser.isEmpty()) {
            return new AddBookPayload(new SecurityError("Not logged in.", SecurityErrorCode.NOT_AUTHENTICATED));
        }

        Optional<Book> book = bookRepository.findById(id);

        if (book.isEmpty()) {
            String errorMessage = "The book with id " + id + " not found.";
            return new AddBookPayload(new ValidationError(errorMessage, ValidationErrorCode.NOT_FOUND));
        }

        userBookRepository.addBook(currentUser.get(), book.get());

        return new AddBookPayload();
    }

}
