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
import com.apollographql.apollo.api.Optional;
import com.apollographql.java.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.SearchProfileQuery;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class SearchProfileQueryTest extends AbstractTest {

    @Test
    void returnsNoProfilesWithoutAMatch() {
        SearchProfileQuery searchProfileQuery =
                new SearchProfileQuery("Z", Optional.Absent.INSTANCE);

        ApolloResponse<SearchProfileQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(searchProfileQuery)).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertTrue(response.data.searchProfile.isEmpty());
    }

    @Test
    void returnsASingleProfile() {
        SearchProfileQuery searchProfileQuery =
                new SearchProfileQuery("t", new Optional.Present<>(1));

        ApolloResponse<SearchProfileQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(searchProfileQuery)).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertEquals(1, response.data.searchProfile.size());
        assertEquals("test", response.data.searchProfile.getFirst().username);
        assertEquals("Test Test", response.data.searchProfile.getFirst().fullName);
    }

}
