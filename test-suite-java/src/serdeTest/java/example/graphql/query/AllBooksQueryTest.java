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
import example.client.ExampleClient.Book;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class AllBooksQueryTest extends AbstractTest {

    @Test
    void returnsAllBooks() {
        GraphQLResponse<List<Book>> response = client.allBooks();

        assertTrue(response.errors().isEmpty());
        assertNotNull(response.data());
        assertEquals(5, response.data().size());

        assertEquals("ab", response.data().getFirst().id());
        assertEquals("The Fractured Sage", response.data().getFirst().title());
        assertEquals(1982, response.data().getFirst().year());
        assertEquals("The Fractured Sage (1982)", response.data().getFirst().label());

        assertEquals(2, response.data().getFirst().sellers().size());
        assertEquals("Amaz", response.data().getFirst().sellers().get(0).name());
        assertEquals(20.0, response.data().getFirst().sellers().get(0).price());
        assertEquals("Yah", response.data().getFirst().sellers().get(1).name());
        assertEquals(21.0, response.data().getFirst().sellers().get(1).price());
    }

}
