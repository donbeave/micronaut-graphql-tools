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

import example.client.ExampleClient.OtherError
import example.client.ExampleClient.Partial
import example.client.ExampleClient.Profile
import example.client.ExampleClient.ProfileName
import example.client.ExampleClient.SecurityError
import example.client.ExampleClient.SignUpInput
import example.client.ExampleClient.ValidationError
import example.client.ExampleClient.ValidationErrorCode
import io.micronaut.graphql.tools.client.exceptions.GraphQLErrorsException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

@MicronautTest
class DeclarativeClientTest(private val client: ExampleClient) {

    @Test
    fun aPayloadMethodReturnsTheRootField() = runBlocking {
        // tag::payload[]
        val profiles = client.profiles("bob", null)

        assertEquals(listOf(Profile("bob", "Bob Di")), profiles)
        // end::payload[]
    }

    @Test
    fun anEnvelopeMethodReturnsTheResponse() = runBlocking {
        // tag::envelope[]
        val response = client.allBooks()

        assertTrue(response.errors().isEmpty())
        assertEquals(5, response.data()!!.size)
        assertEquals(200, response.httpResponse().code())
        // end::envelope[]
    }

    @Test
    fun aMutationTakesAnInputType() = runBlocking {
        // tag::mutation[]
        val response = client.signUp(SignUpInput("mary", "secret", "Mary", "Jones"))

        assertEquals("mary", response.data()!!.data!!.username)
        // end::mutation[]
        assertNull(response.data()!!.error)
    }

    @Test
    fun aUnionIsReadIntoASealedResult() = runBlocking {
        // tag::union[]
        val error = client.signIn("unknown", "x").data()!!.error!!

        val message = when (error) {
            is ValidationError -> error.message
            is SecurityError -> error.message
            is OtherError -> "unknown"
        }

        assertEquals("User unknown not found.", message)
        // end::union[]
        assertEquals(ValidationErrorCode.NOT_FOUND, (error as ValidationError).validationCode)
    }

    @Test
    fun aFragmentOfTheClientDirectoryIsPartOfTheOperation() = runBlocking {
        // tag::fragment[]
        val names = client.profileNames("bob")

        assertEquals(listOf(ProfileName("bob")), names)
        // end::fragment[]
    }

    @Test
    fun javaTimeAndLongRoundTrip() = runBlocking {
        // tag::scalars[]
        assertEquals(Instant.parse("2026-12-24T10:16:30Z"), client.echoInstant(Instant.parse("2026-12-24T10:15:30Z")))
        assertEquals(9007199254740993L, client.echoLong(9007199254740993L))
        // end::scalars[]
    }

    @Test
    fun anErrorKeepsThePartialData() = runBlocking {
        // tag::partial-data[]
        val response = client.partial()

        assertFalse(response.errors().isEmpty())
        assertEquals(listOf<Any>("unreliable"), response.errors().first().path())
        assertEquals("introduced introduced", response.data()!!.introduced)
        assertNull(response.data()!!.unreliable)

        val failure = assertThrows(GraphQLErrorsException::class.java) { response.dataOrThrow() }
        assertEquals("introduced introduced", (failure.response.data() as Partial).introduced)
        // end::partial-data[]
    }
}
