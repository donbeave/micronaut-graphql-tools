package example.graphql.query;

import com.apollographql.apollo3.api.ApolloResponse;
import com.apollographql.apollo3.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.SignUpMutation;
import example.client.type.SignUpInput;
import example.client.type.ValidationErrorCode;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class SignUpMutationTest extends AbstractTest {

    @Test
    void testSuccessRegistration() {
        SignUpMutation signUpMutation =
                new SignUpMutation(new SignUpInput("john", "test", "John", "Johnathan"));

        ApolloResponse<SignUpMutation.Data> response =
                Rx3Apollo.single(apolloClient.mutation(signUpMutation)).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNotNull(response.data.signUp);
        assertNull(response.data.signUp.error);
        assertEquals("john", response.data.signUp.data.username);
        assertEquals("John", response.data.signUp.data.firstName);
        assertEquals("Johnathan", response.data.signUp.data.lastName);
        assertTrue(response.data.signUp.data.books.isEmpty());
    }

    @Test
    void testExistedUsername() {
        SignUpMutation signUpMutation =
                new SignUpMutation(new SignUpInput("test", "test", "Test", "Test"));

        ApolloResponse<SignUpMutation.Data> response =
                Rx3Apollo.single(apolloClient.mutation(signUpMutation)).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNotNull(response.data.signUp);
        assertNull(response.data.signUp.data);
        assertNotNull(response.data.signUp.error);
        assertNull(response.data.signUp.error.onSecurityError);
        assertNotNull(response.data.signUp.error.onValidationError);
        assertEquals(ValidationErrorCode.DUPLICATE, response.data.signUp.error.onValidationError.validationCode);
        assertEquals("User with username test already exists.", response.data.signUp.error.onValidationError.message);
    }

}
