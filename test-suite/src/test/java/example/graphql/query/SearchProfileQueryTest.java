package example.graphql.query;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.apollo.api.Optional;
import com.apollographql.apollo.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.SearchProfileQuery;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class SearchProfileQueryTest extends AbstractTest {

    @Test
    void testEmptyResults() {
        SearchProfileQuery searchProfileQuery =
                new SearchProfileQuery("Z", Optional.Absent.INSTANCE);

        ApolloResponse<SearchProfileQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(searchProfileQuery)).blockingGet();

        assertFalse(response.hasErrors());
        assertTrue(response.data.searchProfile.isEmpty());
    }

    @Test
    void testSingleResult() {
        SearchProfileQuery searchProfileQuery =
                new SearchProfileQuery("t", new Optional.Present<>(1));

        ApolloResponse<SearchProfileQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(searchProfileQuery)).blockingGet();

        assertFalse(response.hasErrors());
        assertEquals(1, response.data.searchProfile.size());
        assertEquals("test", response.data.searchProfile.get(0).username);
        assertEquals("Test Test", response.data.searchProfile.get(0).fullName);
    }

}
