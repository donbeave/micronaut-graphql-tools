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

import example.AbstractTest;
import example.client.ExampleClient.ValidationError;
import example.client.ExampleClient.AuthPayload;
import example.client.ExampleClient.SignUpInput;
import example.client.ExampleClient.ValidationErrorCode;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class SignUpMutationTest extends AbstractTest {

    @Test
    void registersANewUser() {
        GraphQLResponse<AuthPayload> response = client.signUp(new SignUpInput("john", "test", "John", "Johnathan"));

        assertTrue(response.errors().isEmpty());
        assertNotNull(response.data());
        assertNull(response.data().error());
        assertEquals("john", response.data().data().username());
        assertEquals("John", response.data().data().firstName());
        assertEquals("Johnathan", response.data().data().lastName());
        assertTrue(response.data().data().books().isEmpty());
    }

    @Test
    void rejectsAnExistingUsername() {
        GraphQLResponse<AuthPayload> response = client.signUp(new SignUpInput("test", "test", "Test", "Test"));

        assertTrue(response.errors().isEmpty());
        assertNotNull(response.data());
        assertNull(response.data().data());
        assertNotNull(response.data().error());
        ValidationError error = assertInstanceOf(ValidationError.class, response.data().error());
        assertEquals(ValidationErrorCode.DUPLICATE, error.validationCode());
        assertEquals("User with username test already exists.", error.message());
    }

}
