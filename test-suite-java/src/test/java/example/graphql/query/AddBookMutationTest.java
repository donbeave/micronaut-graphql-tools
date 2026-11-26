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

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.java.client.ApolloClient;
import com.apollographql.java.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.AddBookMutation;
import example.client.CurrentUserQuery;
import example.client.type.SecurityErrorCode;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
class AddBookMutationTest extends AbstractTest {

    @Test
    void rejectsAnUnauthenticatedCaller() {
        AddBookMutation addBookMutation =
                new AddBookMutation("ab");

        ApolloResponse<AddBookMutation.Data> response =
                Rx3Apollo.single(apolloClient.mutation(addBookMutation)).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNotNull(response.data.addBook);
        assertNotNull(response.data.addBook.error);
        assertNull(response.data.addBook.error.onValidationError);
        assertNotNull(response.data.addBook.error.onSecurityError);
        assertEquals(SecurityErrorCode.NOT_AUTHENTICATED, response.data.addBook.error.onSecurityError.securityCode);
        assertEquals("Not logged in.", response.data.addBook.error.onSecurityError.message);
    }

    @Test
    void addsABook() {
        ApolloClient bob = clientOf("bob");

        AddBookMutation addBookMutation =
                new AddBookMutation("ab");

        ApolloResponse<AddBookMutation.Data> addBookResponse =
                Rx3Apollo.single(bob.mutation(addBookMutation)).blockingGet();

        assertFalse(addBookResponse.hasErrors());
        assertNotNull(addBookResponse.data);
        assertNotNull(addBookResponse.data.addBook);
        assertNull(addBookResponse.data.addBook.error);

        ApolloResponse<example.client.CurrentUserQuery.Data> currentUserResponse =
                Rx3Apollo.single(bob.query(new CurrentUserQuery())).blockingGet();

        assertFalse(currentUserResponse.hasErrors());
        assertNotNull(currentUserResponse.data);
        assertNotNull(currentUserResponse.data.currentUser);
        assertEquals("bob", currentUserResponse.data.currentUser.username);
        assertEquals(1, currentUserResponse.data.currentUser.books.size());
        assertEquals("ab", currentUserResponse.data.currentUser.books.getFirst().id);
        assertEquals("The Fractured Sage", currentUserResponse.data.currentUser.books.getFirst().title);
        assertEquals(1982, currentUserResponse.data.currentUser.books.getFirst().year);
    }

}
