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
package example.graphql.query;

import example.AbstractTest;
import example.client.ExampleClient.SecurityError;
import example.client.ExampleClient.AddBookPayload;
import example.client.ExampleClient.SecurityErrorCode;
import example.client.ExampleClient.User;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class AddBookMutationTest extends AbstractTest {

    @Test
    void rejectsAnUnauthenticatedCaller() {
        GraphQLResponse<AddBookPayload> response = client.addBook("ab", null);

        assertTrue(response.errors().isEmpty());
        assertNotNull(response.data());
        assertNotNull(response.data().error());
        SecurityError error = assertInstanceOf(SecurityError.class, response.data().error());
        assertEquals(SecurityErrorCode.NOT_AUTHENTICATED, error.securityCode());
        assertEquals("Not logged in.", error.message());
    }

    @Test
    void addsABook() {
        GraphQLResponse<AddBookPayload> addBookResponse = client.addBook("ab", "bob");

        assertTrue(addBookResponse.errors().isEmpty());
        assertNotNull(addBookResponse.data());
        assertNull(addBookResponse.data().error());

        GraphQLResponse<User> currentUserResponse = client.currentUser("bob");

        assertTrue(currentUserResponse.errors().isEmpty());
        assertNotNull(currentUserResponse.data());
        assertEquals("bob", currentUserResponse.data().username());
        assertEquals(1, currentUserResponse.data().books().size());
        assertEquals("ab", currentUserResponse.data().books().getFirst().id());
        assertEquals("The Fractured Sage", currentUserResponse.data().books().getFirst().title());
        assertEquals(1982, currentUserResponse.data().books().getFirst().year());
    }

}
