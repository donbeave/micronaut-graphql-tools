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
    void registersANewUser() {
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
    void rejectsAnExistingUsername() {
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
