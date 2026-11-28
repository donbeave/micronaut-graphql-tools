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
import example.client.ExampleClient.User;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class CurrentUserQueryTest extends AbstractTest {

    @Test
    void returnsNoUserForAGuest() {
        GraphQLResponse<User> response = client.currentUser(null);

        assertTrue(response.errors().isEmpty());
        assertNull(response.data());
    }

    @Test
    void returnsTheAuthenticatedUser() {
        GraphQLResponse<User> response = client.currentUser("test");

        assertTrue(response.errors().isEmpty());
        assertNotNull(response.data());
        assertEquals("test", response.data().username());
        assertEquals("Test", response.data().firstName());
        assertEquals("Test", response.data().lastName());
        assertTrue(response.data().books().isEmpty());
    }

}
