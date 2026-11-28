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
package example.client

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Sealed
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.client.GraphQLResponse
import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation
import io.micronaut.http.annotation.Header
import io.micronaut.serde.annotation.Serdeable

import java.time.Instant

/**
 * The declarative client of the tests: it calls the application of this suite, whose address is the embedded server. The processor
 * checks each operation, a document of {@code src/serdeTest/graphql/client/server}, against the schema of the application and compiles it.
 * The result models are hand-written, because the client mode of the generator does not emit models yet.
 */
@CompileStatic
@GraphQLClient(value = '/', schema = 'server')
interface ExampleClient {

    @Serdeable
    static enum ValidationErrorCode {
        NOT_FOUND, DUPLICATE
    }

    @Serdeable
    static enum SecurityErrorCode {
        INCORRECT_CREDENTIALS, NOT_AUTHENTICATED
    }

    // tag::union[]
    /**
     * The union Error as the operations select it: one subtype for each member that a fragment names, chosen by the field
     * {@code __typename} that the processor adds to the selection, and a default branch for a member that a later schema adds.
     */
    @Serdeable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = '__typename', defaultImpl = OtherError)
    @JsonSubTypes([
            @JsonSubTypes.Type(value = ValidationError, name = 'ValidationError'),
            @JsonSubTypes.Type(value = SecurityError, name = 'SecurityError')
    ])
    @Sealed(permittedSubclasses = [ValidationError, SecurityError, OtherError])
    static interface OperationError {
    }

    @Serdeable
    @Canonical
    static final class ValidationError implements OperationError {
        String message
        ValidationErrorCode validationCode
    }

    @Serdeable
    @Canonical
    static final class SecurityError implements OperationError {
        String message
        SecurityErrorCode securityCode
    }

    @Serdeable
    static final class OtherError implements OperationError {
    }
    // end::union[]

    @Serdeable
    @Canonical
    static class SignUpInput {
        String username
        String password
        String firstName
        String lastName
    }

    @Serdeable
    @Canonical
    static class Seller {
        String name
        double price
    }

    @Serdeable
    @Canonical
    static class Book {
        String id
        String title
        int year
        String label
        List<Seller> sellers
    }

    @Serdeable
    @Canonical
    static class UserBook {
        String id
        String title
        int year
    }

    @Serdeable
    @Canonical
    static class User {
        String username
        String firstName
        String lastName
        List<UserBook> books
    }

    @Serdeable
    @Canonical
    static class Profile {
        String username
        String fullName
    }

    @Serdeable
    @Canonical
    static class ProfileName {
        String username
    }

    @Serdeable
    @Canonical
    static class Partial {
        String introduced
        @Nullable String unreliable
    }

    @Serdeable
    @Canonical
    static class AuthPayload {
        User data
        OperationError error
    }

    @Serdeable
    @Canonical
    static class AddBookPayload {
        OperationError error
    }

    @Serdeable
    @Canonical
    static class Schema {
        String description
    }

    @Serdeable
    @Canonical
    static class Directives {
        String shout
        String tagged
        Schema __schema
    }

    // tag::envelope[]
    @GraphQLOperation('allBooks')
    GraphQLResponse<List<Book>> allBooks()
    // end::envelope[]

    @GraphQLOperation('currentUser')
    GraphQLResponse<User> currentUser(@Nullable @Header('X-User') String user)

    @GraphQLOperation('introduced')
    GraphQLResponse<String> introduced()

    @GraphQLOperation('directives')
    GraphQLResponse<Directives> directives()

    @GraphQLOperation('searchProfile')
    GraphQLResponse<List<Profile>> searchProfile(String contains, @Nullable Integer limit)

    // tag::payload[]
    @GraphQLOperation('searchProfile')
    List<Profile> profiles(String contains, @Nullable Integer limit)
    // end::payload[]

    // tag::fragment[]
    @GraphQLOperation('profileNames')
    List<ProfileName> profileNames(String contains)
    // end::fragment[]

    @GraphQLOperation('signIn')
    GraphQLResponse<AuthPayload> signIn(String username, String password)

    // tag::mutation[]
    @GraphQLOperation('signUp')
    GraphQLResponse<AuthPayload> signUp(SignUpInput input)
    // end::mutation[]

    @GraphQLOperation('addBook')
    GraphQLResponse<AddBookPayload> addBook(String id, @Nullable @Header('X-User') String user)

    // tag::scalars[]
    @GraphQLOperation('query EchoInstant($at: Instant!) { echoInstant(at: $at) }')
    Instant echoInstant(Instant at)

    @GraphQLOperation('query EchoLong($value: Long!) { echoLong(value: $value) }')
    Long echoLong(Long value)
    // end::scalars[]

    // tag::partial-data[]
    @GraphQLOperation('query Partial { introduced unreliable }')
    GraphQLResponse<Partial> partial()
    // end::partial-data[]
}
