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
import example.client.AllBooksQuery;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
class AllBooksQueryTest extends AbstractTest {

    @Test
    void returnsAllBooks() {
        ApolloResponse<AllBooksQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(new AllBooksQuery())).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNotNull(response.data.allBooks);
        assertEquals(5, response.data.allBooks.size());

        assertEquals("ab", response.data.allBooks.getFirst().id);
        assertEquals("The Fractured Sage", response.data.allBooks.getFirst().title);
        assertEquals(1982, response.data.allBooks.getFirst().year);
        assertEquals("The Fractured Sage (1982)", response.data.allBooks.getFirst().label);

        assertEquals(2, response.data.allBooks.getFirst().sellers.size());
        assertEquals("Amaz", response.data.allBooks.getFirst().sellers.get(0).name);
        assertEquals(20.0, response.data.allBooks.getFirst().sellers.get(0).price);
        assertEquals("Yah", response.data.allBooks.getFirst().sellers.get(1).name);
        assertEquals(21.0, response.data.allBooks.getFirst().sellers.get(1).price);
    }

}
