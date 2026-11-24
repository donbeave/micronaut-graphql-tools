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
package example.tester

import io.micronaut.graphql.tools.testing.GraphQLTester
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledInNativeImage

// The tester is test-time code, so this test runs on the JVM only.
@DisabledInNativeImage
@MicronautTest(environments = [TESTER])
class TesterTest {

    @Inject
    lateinit var tester: GraphQLTester

    // tag::data[]
    @Test
    fun `a query returns its data`() {
        val response = tester.execute("{ testerBook(id: \"b1\") { id title } }")

        response.assertNoErrors()
            .assertPath("testerBook.title", "Book b1")
            .assertPath("testerBook", mapOf("id" to "b1", "title" to "Book b1"))
        assertEquals("b1", response.path("testerBook.id", String::class.java).orElseThrow())
    }
    // end::data[]

    @Test
    fun `a null field is null in the data`() {
        tester.execute("{ testerBook(id: \"none\") { id } }").assertNoErrors().assertPathNull("testerBook")
    }

    // tag::variables[]
    @Test
    fun `variables and the operation name are sent`() {
        tester.request("query Pages(\$count: Int!) { testerPages(count: \$count) } query Other { testerPages(count: 1) }")
            .operationName("Pages")
            .variable("count", 21)
            .execute()
            .assertNoErrors()
            .assertPath("testerPages", 42)
    }
    // end::variables[]

    // tag::errors[]
    @Test
    fun `an exception of a resolver is an error of its field`() {
        val response = tester.execute("{ testerFailing }")

        response.assertErrorCount(1).assertErrorPath("testerFailing").assertErrorExtension("code", "INTERNAL_ERROR")
            .assertPathNull("testerFailing")
        assertEquals(1, response.errors.size)
    }
    // end::errors[]

    @Test
    fun `a variable that does not bind is an error of the response`() {
        val response = tester.request("query(\$count: Int!) { testerPages(count: \$count) }")
            .variable("count", "many")
            .execute()

        response.assertErrorCount(1).assertErrorMessage("count")
        assertNull(response.data)
    }

    @Test
    fun `a missing variable and an unknown field are errors`() {
        tester.execute("query(\$count: Int!) { testerPages(count: \$count) }").assertErrorCount(1).assertErrorMessage("count")
        tester.execute("{ nothing }").assertErrorCount(1).assertErrorMessage("nothing")
    }

    // tag::context[]
    @Test
    fun `a context value reaches the resolver`() {
        tester.request("{ testerWhoAmI }").context("tester.user", "alice").execute()
            .assertNoErrors()
            .assertPath("testerWhoAmI", "alice")
    }
    // end::context[]

    @Test
    fun `a missing context value is an error of the field`() {
        tester.execute("{ testerWhoAmI }").assertErrorCount(1).assertErrorPath("testerWhoAmI")
    }

    @Test
    fun `an assertion that does not hold is an assertion error`() {
        val response = tester.execute("{ testerPages(count: 1) }")

        assertThrows(AssertionError::class.java) { response.assertPath("testerPages", 3) }
        assertThrows(AssertionError::class.java) { response.assertErrorCount(1) }
    }
}
