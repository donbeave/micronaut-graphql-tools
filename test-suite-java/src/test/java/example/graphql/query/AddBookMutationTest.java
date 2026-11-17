package example.graphql.query;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.apollo.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.AddBookMutation;
import example.client.CurrentUserQuery;
import example.client.type.SecurityErrorCode;
import example.data.AuthenticationService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
class AddBookMutationTest extends AbstractTest {

    @Inject AuthenticationService authenticationService;

    @AfterEach
    public void cleanup() {
        authenticationService.logout();
    }

    @Test
    void testNotAuthenticated() {
        AddBookMutation addBookMutation =
                new AddBookMutation("ab");

        ApolloResponse<AddBookMutation.Data> response =
                Rx3Apollo.single(apolloClient.mutation(addBookMutation)).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNotNull(response.data.addBook);
        assertNotNull(response.data.addBook.error);
        assertNull(response.data.addBook.error.onValidationError);
        assertNotNull(response.data.addBook.error.onSecurityError);
        assertEquals(SecurityErrorCode.NOT_AUTHENTICATED, response.data.addBook.error.onSecurityError.securityCode);
        assertEquals("Not logged in.", response.data.addBook.error.onSecurityError.message);
    }

    @Test
    void testAddBook() {
        authenticationService.login("bob", "bodi");

        AddBookMutation addBookMutation =
                new AddBookMutation("ab");

        ApolloResponse<AddBookMutation.Data> addBookResponse =
                Rx3Apollo.single(apolloClient.mutation(addBookMutation)).blockingGet();

        assertFalse(addBookResponse.hasErrors());
        assertNotNull(addBookResponse.data);
        assertNotNull(addBookResponse.data.addBook);
        assertNull(addBookResponse.data.addBook.error);

        ApolloResponse<example.client.CurrentUserQuery.Data> currentUserResponse =
                Rx3Apollo.single(apolloClient.query(new CurrentUserQuery())).blockingGet();

        assertFalse(currentUserResponse.hasErrors());
        assertNotNull(currentUserResponse.data);
        assertNotNull(currentUserResponse.data.currentUser);
        assertEquals("bob", currentUserResponse.data.currentUser.username);
        assertEquals(1, currentUserResponse.data.currentUser.books.size());
        assertEquals("ab", currentUserResponse.data.currentUser.books.getFirst().id);
        assertEquals("The Fractured Sage", currentUserResponse.data.currentUser.books.getFirst().title);
        assertEquals(1982, currentUserResponse.data.currentUser.books.getFirst().year);
    }

}
