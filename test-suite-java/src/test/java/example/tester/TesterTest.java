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
package example.tester;

import io.micronaut.graphql.tools.testing.GraphQLTestResponse;
import io.micronaut.graphql.tools.testing.GraphQLTester;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// The tester is test-time code, so this test runs on the JVM only.
@DisabledInNativeImage
@MicronautTest(environments = TesterFixtures.TESTER)
class TesterTest {

    @Inject
    GraphQLTester tester;

    // tag::data[]
    @Test
    void aQueryReturnsItsData() {
        GraphQLTestResponse response = tester.execute("{ testerBook(id: \"b1\") { id title } }");

        response.assertNoErrors()
                .assertPath("testerBook.title", "Book b1")
                .assertPath("testerBook", Map.of("id", "b1", "title", "Book b1"));
        assertEquals("b1", response.path("testerBook.id", String.class).orElseThrow());
    }
    // end::data[]

    @Test
    void aNullFieldIsNullInTheData() {
        tester.execute("{ testerBook(id: \"none\") { id } }").assertNoErrors().assertPathNull("testerBook");
    }

    // tag::variables[]
    @Test
    void variablesAndTheOperationNameAreSent() {
        tester.request("query Pages($count: Int!) { testerPages(count: $count) } query Other { testerPages(count: 1) }")
                .operationName("Pages")
                .variable("count", 21)
                .execute()
                .assertNoErrors()
                .assertPath("testerPages", 42);
    }
    // end::variables[]

    // tag::errors[]
    @Test
    void anExceptionOfAResolverIsAnErrorOfItsField() {
        GraphQLTestResponse response = tester.execute("{ testerFailing }");

        response.assertErrorCount(1).assertErrorPath("testerFailing").assertErrorExtension("code", "INTERNAL_ERROR")
                .assertPathNull("testerFailing");
        assertEquals(1, response.getErrors().size());
    }
    // end::errors[]

    @Test
    void aVariableThatDoesNotBindIsAnErrorOfTheResponse() {
        GraphQLTestResponse response = tester.request("query($count: Int!) { testerPages(count: $count) }")
                .variable("count", "many")
                .execute();

        response.assertErrorCount(1).assertErrorMessage("count");
        assertNull(response.getData());
    }

    @Test
    void aMissingVariableAndAnUnknownFieldAreErrors() {
        tester.execute("query($count: Int!) { testerPages(count: $count) }").assertErrorCount(1).assertErrorMessage("count");
        tester.execute("{ nothing }").assertErrorCount(1).assertErrorMessage("nothing");
    }

    // tag::context[]
    @Test
    void aContextValueReachesTheResolver() {
        tester.request("{ testerWhoAmI }").context("tester.user", "alice").execute()
                .assertNoErrors()
                .assertPath("testerWhoAmI", "alice");
    }
    // end::context[]

    @Test
    void aMissingContextValueIsAnErrorOfTheField() {
        tester.execute("{ testerWhoAmI }").assertErrorCount(1).assertErrorPath("testerWhoAmI");
    }

    @Test
    void anAssertionThatDoesNotHoldIsAnAssertionError() {
        GraphQLTestResponse response = tester.execute("{ testerPages(count: 1) }");

        assertThrows(AssertionError.class, () -> response.assertPath("testerPages", 3));
        assertThrows(AssertionError.class, () -> response.assertErrorCount(1));
    }

}
