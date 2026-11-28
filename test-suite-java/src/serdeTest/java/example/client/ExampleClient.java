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
package example.client;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.http.annotation.Header;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * The declarative client of the tests: it calls the application of this suite, whose address is the embedded server. The processor
 * checks each operation, a document of {@code src/test/graphql/client/server}, against the schema of the application and compiles it.
 * The result models are hand-written, because the client mode of the generator does not emit models yet.
 */
@GraphQLClient(value = "/", schema = "server")
public interface ExampleClient {

    /** The header that the example server reads the user from. */
    String USER_HEADER = "X-User";

    @Serdeable
    enum ValidationErrorCode {
        NOT_FOUND, DUPLICATE
    }

    @Serdeable
    enum SecurityErrorCode {
        INCORRECT_CREDENTIALS, NOT_AUTHENTICATED
    }

    /**
     * The union Error as the operations select it: one subtype for each member that a fragment names, chosen by the field
     * {@code __typename} that the processor adds to the selection, and a default branch for a member that a later schema adds.
     */
    @Serdeable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename", defaultImpl = OtherError.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ValidationError.class, name = "ValidationError"),
            @JsonSubTypes.Type(value = SecurityError.class, name = "SecurityError")
    })
    sealed interface OperationError permits ValidationError, SecurityError, OtherError {
    }

    @Serdeable
    record ValidationError(String message, ValidationErrorCode validationCode) implements OperationError {
    }

    @Serdeable
    record SecurityError(String message, SecurityErrorCode securityCode) implements OperationError {
    }

    @Serdeable
    record OtherError() implements OperationError {
    }

    @Serdeable
    record SignUpInput(String username, String password, String firstName, String lastName) {
    }

    @Serdeable
    record Seller(String name, double price) {
    }

    @Serdeable
    record Book(String id, String title, int year, String label, List<Seller> sellers) {
    }

    @Serdeable
    record UserBook(String id, String title, int year) {
    }

    @Serdeable
    record User(String username, String firstName, String lastName, List<UserBook> books) {
    }

    @Serdeable
    record Profile(String username, String fullName) {
    }

    @Serdeable
    record AuthPayload(User data, OperationError error) {
    }

    @Serdeable
    record AddBookPayload(OperationError error) {
    }

    @Serdeable
    record Schema(String description) {
    }

    @Serdeable
    record Directives(String shout, String tagged, Schema __schema) {
    }

    @GraphQLOperation("allBooks")
    GraphQLResponse<List<Book>> allBooks();

    @GraphQLOperation("currentUser")
    GraphQLResponse<User> currentUser(@Nullable @Header(USER_HEADER) String user);

    @GraphQLOperation("introduced")
    GraphQLResponse<String> introduced();

    @GraphQLOperation("directives")
    GraphQLResponse<Directives> directives();

    @GraphQLOperation("searchProfile")
    GraphQLResponse<List<Profile>> searchProfile(String contains, @Nullable Integer limit);

    @GraphQLOperation("signIn")
    GraphQLResponse<AuthPayload> signIn(String username, String password);

    @GraphQLOperation("signUp")
    GraphQLResponse<AuthPayload> signUp(SignUpInput input);

    @GraphQLOperation("addBook")
    GraphQLResponse<AddBookPayload> addBook(String id, @Nullable @Header(USER_HEADER) String user);
}
