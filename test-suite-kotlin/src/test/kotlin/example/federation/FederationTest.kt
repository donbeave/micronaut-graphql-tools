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
package example.federation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): a subgraph that serves _service and _entities, with strict mode at its default.
class FederationTest {

    private val prefix = "micronaut.graphql.tools."

    // tag::settings[]
    private val settings = mapOf(
        "${prefix}allow.inert-directives" to listOf("key"),
        "${prefix}allow.unreachable" to listOf("Order")
    )
    // end::settings[]

    private fun execute(context: ApplicationContext, query: String, variables: Map<String, Any> = emptyMap()): ExecutionResult =
        GraphQL.newGraphQL(context.getBean(GraphQLSchema::class.java)).build()
            .execute(ExecutionInput.newExecutionInput().query(query).variables(variables).build())

    @Test
    fun `the service field returns the sdl of the subgraph`() {
        ApplicationContext.run(settings, FEDERATION).use { context ->
            val result = execute(context, "{ _service { sdl } }")
            assertTrue(result.errors.isEmpty(), result.errors.toString())
            val sdl = (result.getData<Map<String, Map<String, String>>>()!!["_service"])!!["sdl"]!!
            assertTrue(sdl.contains("type Product @key(fields : \"id\")"), sdl)
            assertTrue(sdl.contains("type Order @key(fields : \"id\")"), sdl)
        }
    }

    // tag::entities-query[]
    @Test
    fun `entities resolves representations in order`() {
        ApplicationContext.run(settings, FEDERATION).use { context ->
            val result = execute(
                context,
                "query (\$representations: [_Any!]!) { _entities(representations: \$representations) {" +
                    " __typename ... on Product { id name } ... on Order { id total } } }",
                mapOf(
                    "representations" to listOf(
                        mapOf("__typename" to "Order", "id" to "o1"),
                        mapOf("__typename" to "Product", "id" to "p1")
                    )
                )
            )
            assertTrue(result.errors.isEmpty(), result.errors.toString())
            assertEquals(
                mapOf(
                    "_entities" to listOf(
                        mapOf("__typename" to "Order", "id" to "o1", "total" to 42),
                        mapOf("__typename" to "Product", "id" to "p1", "name" to "Product p1")
                    )
                ),
                result.getData<Any>()
            )
        }
    }
    // end::entities-query[]

    @Test
    fun `the regular fields still work`() {
        ApplicationContext.run(settings, FEDERATION).use { context ->
            val result = execute(context, "{ product(id: \"p2\") { id name } }")
            assertTrue(result.errors.isEmpty(), result.errors.toString())
            assertEquals(mapOf("product" to mapOf("id" to "p2", "name" to "Product p2")), result.getData<Any>())
        }
    }

    @Test
    fun `invalid representations are errors`() {
        ApplicationContext.run(settings, FEDERATION).use { context ->
            val query = "query (\$r: [_Any!]!) { _entities(representations: \$r) { __typename } }"
            val huge = (0..100).map { mapOf("__typename" to "Product", "id" to "p$it") }
            val invalid = listOf<List<Any>>(
                listOf("Product"),
                listOf(mapOf("id" to "p1")),
                listOf(mapOf("__typename" to 7, "id" to "p1")),
                listOf(mapOf("__typename" to "Unknown", "id" to "p1")),
                huge
            )
            invalid.forEach { representations ->
                assertFalse(execute(context, query, mapOf("r" to representations)).errors.isEmpty(), representations.toString())
            }
        }
    }

    // tag::strict[]
    @Test
    fun `strict mode needs the two allow settings`() {
        assertFindings(mapOf("${prefix}allow.unreachable" to listOf("Order")), "unwired-directive")
        assertFindings(mapOf("${prefix}allow.inert-directives" to listOf("key")), "unreachable-type")
    }
    // end::strict[]

    private fun assertFindings(properties: Map<String, Any>, code: String) {
        var e: Throwable = assertThrows(Exception::class.java) { ApplicationContext.run(properties, FEDERATION).close() }
        while (e !is SchemaBindingException && e.cause != null && e.cause !== e) {
            e = e.cause!!
        }
        assertTrue(e is SchemaBindingException, e.toString())
        assertTrue(e.message!!.contains(code), e.message)
    }
}
