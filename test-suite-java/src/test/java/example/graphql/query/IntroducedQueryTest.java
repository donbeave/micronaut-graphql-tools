package example.graphql.query;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.java.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.IntroducedQuery;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
class IntroducedQueryTest extends AbstractTest {

    @Test
    void testIntroductionAdviceResolver() {
        ApolloResponse<IntroducedQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(new IntroducedQuery())).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertEquals("introduced introduced", response.data.introduced);
    }

}
