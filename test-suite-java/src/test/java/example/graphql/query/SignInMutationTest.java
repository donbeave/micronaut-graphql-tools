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
import example.client.SignInMutation;
import example.client.type.SecurityErrorCode;
import example.client.type.ValidationErrorCode;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class SignInMutationTest extends AbstractTest {

    @Test
    void testSuccessAuthentication() {
        SignInMutation signInMutation =
                new SignInMutation("test", "test");

        ApolloResponse<SignInMutation.Data> response =
                Rx3Apollo.single(apolloClient.mutation(signInMutation)).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNotNull(response.data.signIn);
        assertNull(response.data.signIn.error);
        assertEquals("test", response.data.signIn.data.username);
        assertEquals("Test", response.data.signIn.data.firstName);
        assertEquals("Test", response.data.signIn.data.lastName);
        assertTrue(response.data.signIn.data.books.isEmpty());
    }

    @Test
    void testUnknownUser() {
        SignInMutation signInMutation =
                new SignInMutation("unknown-user", "test");

        ApolloResponse<SignInMutation.Data> response =
                Rx3Apollo.single(apolloClient.mutation(signInMutation)).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNotNull(response.data.signIn);
        assertNull(response.data.signIn.data);
        assertNotNull(response.data.signIn.error);
        assertNull(response.data.signIn.error.onSecurityError);
        assertNotNull(response.data.signIn.error.onValidationError);
        assertEquals(ValidationErrorCode.NOT_FOUND, response.data.signIn.error.onValidationError.validationCode);
        assertEquals("User unknown-user not found.", response.data.signIn.error.onValidationError.message);
    }

    @Test
    void testWrongPassword() {
        SignInMutation signInMutation =
                new SignInMutation("test", "xyz");

        ApolloResponse<SignInMutation.Data> response =
                Rx3Apollo.single(apolloClient.mutation(signInMutation)).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertNotNull(response.data.signIn);
        assertNull(response.data.signIn.data);
        assertNotNull(response.data.signIn.error);
        assertNull(response.data.signIn.error.onValidationError);
        assertNotNull(response.data.signIn.error.onSecurityError);
        assertEquals(SecurityErrorCode.INCORRECT_CREDENTIALS, response.data.signIn.error.onSecurityError.securityCode);
        assertEquals("Password is incorrect.", response.data.signIn.error.onSecurityError.message);
    }

}
