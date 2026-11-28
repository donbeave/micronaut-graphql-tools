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
import example.client.ExampleClient.Profile;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class SearchProfileQueryTest extends AbstractTest {

    @Test
    void returnsNoProfilesWithoutAMatch() {
        GraphQLResponse<List<Profile>> response = client.searchProfile("Z", null);

        assertTrue(response.errors().isEmpty());
        assertNotNull(response.data());
        assertTrue(response.data().isEmpty());
    }

    @Test
    void returnsASingleProfile() {
        GraphQLResponse<List<Profile>> response = client.searchProfile("t", 1);

        assertTrue(response.errors().isEmpty());
        assertNotNull(response.data());
        assertEquals(1, response.data().size());
        assertEquals("test", response.data().getFirst().username());
        assertEquals("Test Test", response.data().getFirst().fullName());
    }

}
