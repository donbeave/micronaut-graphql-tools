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
package example.client;

import example.AbstractTest;
import example.client.ExampleClient.AuthPayload;
import example.client.ExampleClient.Book;
import example.client.ExampleClient.OperationError;
import example.client.ExampleClient.OtherError;
import example.client.ExampleClient.Partial;
import example.client.ExampleClient.Profile;
import example.client.ExampleClient.ProfileName;
import example.client.ExampleClient.SecurityError;
import example.client.ExampleClient.SignUpInput;
import example.client.ExampleClient.ValidationError;
import example.client.ExampleClient.ValidationErrorCode;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.exceptions.GraphQLErrorsException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class DeclarativeClientTest extends AbstractTest {

    @Test
    void aPayloadMethodReturnsTheRootField() {
        // tag::payload[]
        List<Profile> profiles = client.profiles("bob", null);

        assertEquals(List.of(new Profile("bob", "Bob Di")), profiles);
        // end::payload[]
    }

    @Test
    void anEnvelopeMethodReturnsTheResponse() {
        // tag::envelope[]
        GraphQLResponse<List<Book>> response = client.allBooks();

        assertTrue(response.errors().isEmpty());
        assertEquals(5, response.data().size());
        assertEquals(200, response.httpResponse().code());
        // end::envelope[]
    }

    @Test
    void aMutationTakesAnInputType() {
        // tag::mutation[]
        GraphQLResponse<AuthPayload> response = client.signUp(new SignUpInput("mary", "secret", "Mary", "Jones"));

        assertEquals("mary", response.data().data().username());
        // end::mutation[]
        assertNull(response.data().error());
    }

    @Test
    void aUnionIsReadIntoASealedResult() {
        // tag::union[]
        OperationError error = client.signIn("unknown", "x").data().error();

        String message = switch (error) {
            case ValidationError validation -> validation.message();
            case SecurityError security -> security.message();
            case OtherError other -> "unknown";
        };

        assertEquals("User unknown not found.", message);
        // end::union[]
        assertEquals(ValidationErrorCode.NOT_FOUND, ((ValidationError) error).validationCode());
    }

    @Test
    void aFragmentOfTheClientDirectoryIsPartOfTheOperation() {
        // tag::fragment[]
        List<ProfileName> names = client.profileNames("bob");

        assertEquals(List.of(new ProfileName("bob")), names);
        // end::fragment[]
    }

    @Test
    void javaTimeAndLongRoundTrip() {
        // tag::scalars[]
        assertEquals(Instant.parse("2026-12-24T10:16:30Z"), client.echoInstant(Instant.parse("2026-12-24T10:15:30Z")));
        assertEquals(9007199254740993L, client.echoLong(9007199254740993L));
        // end::scalars[]
    }

    @Test
    void anErrorKeepsThePartialData() {
        // tag::partial-data[]
        GraphQLResponse<Partial> response = client.partial();

        assertFalse(response.errors().isEmpty());
        assertEquals(List.of("unreliable"), response.errors().getFirst().path());
        assertEquals("introduced introduced", response.data().introduced());
        assertNull(response.data().unreliable());

        GraphQLErrorsException failure = assertThrows(GraphQLErrorsException.class, response::dataOrThrow);
        assertEquals("introduced introduced", ((Partial) failure.getResponse().data()).introduced());
        // end::partial-data[]
    }

}
