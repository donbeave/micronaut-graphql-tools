package example.graphql.query;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.apollo.rx3.Rx3Apollo;
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
