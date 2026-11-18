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
import com.apollographql.java.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.CurrentUserQuery;
import example.data.AuthenticationService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class CurrentUserQueryTest extends AbstractTest {

    @Inject AuthenticationService authenticationService;

    @AfterEach
    public void cleanup() {
        authenticationService.logout();
    }

    @Test
    void testGuest() {
        ApolloResponse<CurrentUserQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(new CurrentUserQuery())).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNull(response.data.currentUser);
    }

    @Test
    void testAuthenticated() {
        authenticationService.login("test", "test");

        ApolloResponse<CurrentUserQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(new CurrentUserQuery())).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNotNull(response.data.currentUser);
        assertEquals("test", response.data.currentUser.username);
        assertEquals("Test", response.data.currentUser.firstName);
        assertEquals("Test", response.data.currentUser.lastName);
        assertTrue(response.data.currentUser.books.isEmpty());
    }

}
