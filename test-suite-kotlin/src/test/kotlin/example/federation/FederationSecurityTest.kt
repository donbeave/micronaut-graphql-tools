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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Runs on the JVM and as a native binary (nativeTest): the callers of a subgraph over HTTP, with Micronaut Security and the security module.
class FederationSecurityTest {

    private val service = "{ _service { sdl } }"

    private val product = "{ _entities(representations: [{__typename: \"Product\", id: \"p1\"}]) { __typename ... on Product { id name } } }"

    private val order = "{ _entities(representations: [{__typename: \"Order\", id: \"o1\"}]) { __typename ... on Order { id total } } }"

    private val anonymous = mapOf<String, String>()

    private val user = mapOf("X-User" to "ursula", "X-Roles" to "ROLE_USER")

    private val admin = mapOf("X-User" to "adam", "X-Roles" to "ROLE_ADMIN")

    private val billing = mapOf("X-User" to "bill", "X-Roles" to "ROLE_BILLING")

    private class Reply(val status: Int, val text: String, val body: Map<String, Any?>)

    private fun start(route: String, more: Map<String, Any> = emptyMap(), vararg environments: String): ApplicationContext {
        val properties = mapOf<String, Any>(
            "micronaut.graphql.tools.allow.inert-directives" to listOf("key"),
            "micronaut.graphql.tools.allow.unreachable" to listOf("Order"),
            "micronaut.server.host" to "127.0.0.1",
            "micronaut.server.port" to -1,
            "micronaut.security.enabled" to true,
            "micronaut.security.intercept-url-map[0].pattern" to "/graphql",
            "micronaut.security.intercept-url-map[0].access[0]" to route
        ) + more
        val context = ApplicationContext.builder(FEDERATION, *environments).properties(properties).start()
        context.getBean(EmbeddedServer::class.java).start()
        return context
    }

    private fun post(context: ApplicationContext, headers: Map<String, String>, query: String): Reply {
        val server = context.getBean(EmbeddedServer::class.java)
        val json = context.getBean(JsonMapper::class.java)
        val request = HttpRequest.newBuilder(URI.create("http://${server.host}:${server.port}/graphql"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(mapOf("query" to query))))
        headers.forEach { (name, value) -> request.header(name, value) }
        HttpClient.newHttpClient().use { client ->
            val response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
            val body: Map<String, Any?> =
                if (response.statusCode() == 200) json.readValue(response.body(), Argument.mapOf(String::class.java, Any::class.java)) else emptyMap()
            return Reply(response.statusCode(), response.body(), body)
        }
    }

    private fun path(value: Any?, vararg keys: Any): Any? {
        var current = value
        for (key in keys) {
            current = if (key is Int) (current as? List<*>)?.getOrNull(key) else (current as? Map<*, *>)?.get(key)
        }
        return current
    }

    private fun assertForbidden(reply: Reply) {
        assertEquals(200, reply.status, reply.text)
        assertNull(reply.body["data"], reply.text)
        assertEquals("FORBIDDEN", path(reply.body, "errors", 0, "extensions", "code"), reply.text)
    }

    // tag::route[]
    @Test
    fun `a route rule stops anonymous callers before any field runs`() {
        start("isAuthenticated()").use { context ->
            val serviceReply = post(context, anonymous, service)
            assertEquals(401, serviceReply.status)
            assertFalse(serviceReply.text.contains("type Product"), serviceReply.text)
            val entities = post(context, anonymous, product)
            assertEquals(401, entities.status)
            assertFalse(entities.text.contains("Product p1"), entities.text)

            assertForbidden(post(context, user, service))
            assertForbidden(post(context, user, product))
            val adminReply = post(context, admin, product)
            assertEquals("Product p1", path(adminReply.body, "data", "_entities", 0, "name"), adminReply.text)
        }
    }
    // end::route[]

    // tag::guard[]
    @Test
    fun `the guard decides for each entity type`() {
        start("isAnonymous()").use { context ->
            assertForbidden(post(context, anonymous, product))
            assertForbidden(post(context, user, product))
            assertForbidden(post(context, admin, order))
            assertForbidden(post(context, billing, product))

            val productReply = post(context, admin, product)
            assertEquals(
                mapOf("_entities" to listOf(mapOf("__typename" to "Product", "id" to "p1", "name" to "Product p1"))),
                productReply.body["data"], productReply.text
            )
            val orderReply = post(context, billing, order)
            assertEquals(
                mapOf("_entities" to listOf(mapOf("__typename" to "Order", "id" to "o1", "total" to 42))),
                orderReply.body["data"], orderReply.text
            )

            val mixed = post(
                context, admin,
                "{ _entities(representations: [{__typename: \"Product\", id: \"p1\"}," +
                    " {__typename: \"Order\", id: \"o1\"}]) { __typename } }"
            )
            assertNull(mixed.body["data"], mixed.text)
            assertEquals("FORBIDDEN", path(mixed.body, "errors", 0, "extensions", "code"), mixed.text)
        }
    }
    // end::guard[]

    // tag::secured-and-entities[]
    @Test
    fun `secured on a resolver does not reach entities but the guard does`() {
        val field = "{ product(id: \"p1\") { id name } }"
        start("isAnonymous()", emptyMap(), UNGUARDED).use { context ->
            val secured = post(context, anonymous, field)
            assertNull(path(secured.body, "data", "product"), secured.text)
            assertEquals("UNAUTHENTICATED", path(secured.body, "errors", 0, "extensions", "code"), secured.text)

            val entities = post(context, anonymous, product)
            assertEquals("Product p1", path(entities.body, "data", "_entities", 0, "name"), entities.text)
        }
        start("isAnonymous()").use { context ->
            assertForbidden(post(context, anonymous, product))
            val secured = post(context, admin, field)
            assertEquals("Product p1", path(secured.body, "data", "product", "name"), secured.text)
        }
    }
    // end::secured-and-entities[]

    // tag::service[]
    @Test
    fun `the service field returns the sdl with introspection off to a caller the guard lets through`() {
        start("isAnonymous()", mapOf("micronaut.graphql.tools.protection.introspection" to false)).use { context ->
            val schema = post(context, admin, "{ __schema { queryType { name } } }")
            assertNull(schema.body["data"], schema.text)
            assertFalse((schema.body["errors"] as List<*>).isEmpty(), schema.text)

            val serviceReply = post(context, admin, service)
            assertTrue(path(serviceReply.body, "data", "_service", "sdl").toString().contains("type Product @key"), serviceReply.text)

            assertForbidden(post(context, anonymous, service))
            assertForbidden(post(context, billing, service))
        }
    }
    // end::service[]
}
