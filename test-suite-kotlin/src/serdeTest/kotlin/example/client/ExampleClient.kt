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
import io.micronaut.graphql.tools.client.GraphQLResponse
import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation
import io.micronaut.http.annotation.Header
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant

/**
 * The declarative client of the tests: it calls the application of this suite, whose address is the embedded server. The processor
 * checks each operation, a document of `src/serdeTest/graphql/client/server`, against the schema of the application and compiles it.
 * The result models are hand-written, because the client mode of the generator does not emit models yet.
 */
@GraphQLClient("/", schema = "server")
interface ExampleClient {

    @Serdeable
    enum class ValidationErrorCode {
        NOT_FOUND, DUPLICATE
    }

    @Serdeable
    enum class SecurityErrorCode {
        INCORRECT_CREDENTIALS, NOT_AUTHENTICATED
    }

    // tag::union[]
    /**
     * The union Error as the operations select it: one subtype for each member that a fragment names, chosen by the field
     * `__typename` that the processor adds to the selection, and a default branch for a member that a later schema adds.
     */
    @Serdeable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename", defaultImpl = OtherError::class)
    @JsonSubTypes(
        JsonSubTypes.Type(value = ValidationError::class, name = "ValidationError"),
        JsonSubTypes.Type(value = SecurityError::class, name = "SecurityError")
    )
    sealed interface OperationError

    @Serdeable
    data class ValidationError(val message: String, val validationCode: ValidationErrorCode) : OperationError

    @Serdeable
    data class SecurityError(val message: String, val securityCode: SecurityErrorCode) : OperationError

    @Serdeable
    class OtherError : OperationError
    // end::union[]

    @Serdeable
    data class SignUpInput(val username: String, val password: String, val firstName: String, val lastName: String)

    @Serdeable
    data class Seller(val name: String, val price: Double)

    @Serdeable
    data class Book(val id: String, val title: String, val year: Int, val label: String, val sellers: List<Seller>)

    @Serdeable
    data class UserBook(val id: String, val title: String, val year: Int)

    @Serdeable
    data class User(val username: String, val firstName: String, val lastName: String, val books: List<UserBook>)

    @Serdeable
    data class Profile(val username: String, val fullName: String)

    @Serdeable
    data class ProfileName(val username: String)

    @Serdeable
    data class Partial(val introduced: String, val unreliable: String?)

    @Serdeable
    data class AuthPayload(val data: User?, val error: OperationError?)

    @Serdeable
    data class AddBookPayload(val error: OperationError?)

    @Serdeable
    data class Schema(val description: String?)

    @Serdeable
    data class Directives(val shout: String, val tagged: String, val __schema: Schema)

    // tag::envelope[]
    @GraphQLOperation("allBooks")
    suspend fun allBooks(): GraphQLResponse<List<Book>>
    // end::envelope[]

    @GraphQLOperation("currentUser")
    suspend fun currentUser(@Header("X-User") user: String?): GraphQLResponse<User?>

    @GraphQLOperation("introduced")
    suspend fun introduced(): GraphQLResponse<String>

    @GraphQLOperation("directives")
    suspend fun directives(): GraphQLResponse<Directives>

    @GraphQLOperation("searchProfile")
    suspend fun searchProfile(contains: String, limit: Int?): GraphQLResponse<List<Profile>>

    // tag::payload[]
    @GraphQLOperation("searchProfile")
    suspend fun profiles(contains: String, limit: Int?): List<Profile>
    // end::payload[]

    // tag::fragment[]
    @GraphQLOperation("profileNames")
    suspend fun profileNames(contains: String): List<ProfileName>
    // end::fragment[]

    @GraphQLOperation("signIn")
    suspend fun signIn(username: String, password: String): GraphQLResponse<AuthPayload>

    // tag::mutation[]
    @GraphQLOperation("signUp")
    suspend fun signUp(input: SignUpInput): GraphQLResponse<AuthPayload>
    // end::mutation[]

    @GraphQLOperation("addBook")
    suspend fun addBook(id: String, @Header("X-User") user: String?): GraphQLResponse<AddBookPayload>

    // tag::scalars[]
    @GraphQLOperation("query EchoInstant(\$at: Instant!) { echoInstant(at: \$at) }")
    suspend fun echoInstant(at: Instant): Instant

    @GraphQLOperation("query EchoLong(\$value: Long!) { echoLong(value: \$value) }")
    suspend fun echoLong(value: Long): Long
    // end::scalars[]

    // tag::partial-data[]
    @GraphQLOperation("query Partial { introduced unreliable }")
    suspend fun partial(): GraphQLResponse<Partial>
    // end::partial-data[]
}
