package example.graphql.query;

import com.apollographql.apollo3.api.ApolloResponse;
import com.apollographql.apollo3.rx3.Rx3Apollo;
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
    void testAll() {
        ApolloResponse<AllBooksQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(new AllBooksQuery())).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNotNull(response.data.allBooks);
        assertEquals(5, response.data.allBooks.size());

        assertEquals("ab", response.data.allBooks.get(0).id);
        assertEquals("The Fractured Sage", response.data.allBooks.get(0).title);
        assertEquals(1982, response.data.allBooks.get(0).year);

        assertEquals(2, response.data.allBooks.get(0).sellers.size());
        assertEquals("Amaz", response.data.allBooks.get(0).sellers.get(0).name);
        assertEquals(20.0, response.data.allBooks.get(0).sellers.get(0).price);
        assertEquals("Yah", response.data.allBooks.get(0).sellers.get(1).name);
        assertEquals(21.0, response.data.allBooks.get(0).sellers.get(1).price);
    }

}
