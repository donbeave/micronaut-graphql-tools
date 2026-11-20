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
package example.suspend

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.graphql.tools.internal.execution.SuspendInvoker
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import jakarta.inject.Inject
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation

// Runs on the JVM and as a native binary (nativeTest): Kotlin suspend resolvers over HTTP.
@MicronautTest(environments = [SUSPEND_RESOLVERS])
class SuspendTest(@Client("/") private val client: HttpClient) {

    @Inject
    lateinit var context: ApplicationContext


    private fun post(query: String, request: String? = null): Map<String, Any?> {
        var http = HttpRequest.POST("/graphql", mapOf("query" to query))
        if (request != null) {
            http = http.header(SUSPEND_REQUEST_HEADER, request)
        }
        return client.toBlocking().retrieve(http, Argument.mapOf(Argument.STRING, Argument.OBJECT_ARGUMENT))
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.data() = this["data"] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.obj(key: String) = this[key] as Map<String, Any?>

    @Test
    fun `a suspend query returns its data`() {
        val response = post("{ shelf(id: \"s1\") { id title label } }")
        assertNull(response["errors"], response.toString())
        assertEquals(mapOf("id" to "s1", "title" to "Shelf s1", "label" to "Shelf s1 (s1)"), response.data().obj("shelf"))
    }

    @Test
    fun `a suspend query returns null and a list`() {
        val response = post("{ none: shelf(id: \"none\") { id } shelfTitles(prefix: \"t\") }")
        assertNull(response["errors"], response.toString())
        assertNull(response.data()["none"])
        assertEquals(listOf("t1", "t2"), response.data()["shelfTitles"])
    }

    @Test
    fun `a suspend mutation returns its data`() {
        val response = post("mutation { renameShelf(id: \"s2\", title: \"Renamed\") { id title } }")
        assertNull(response["errors"], response.toString())
        assertEquals(mapOf("id" to "s2", "title" to "Renamed"), response.data().obj("renameShelf"))
    }

    @Test
    fun `a suspend type resolver method gets its parent`() {
        val response = post("{ shelf(id: \"abc\") { sellers } }")
        assertNull(response["errors"], response.toString())
        assertEquals(3, response.data().obj("shelf")["sellers"])
    }

    @Test
    fun `a suspend function that never suspends is a resolver`() {
        assertEquals("immediate", post("{ immediate }").data()["immediate"])
    }

    @Test
    fun `the propagated context is visible in the coroutine, after withContext, and in the children`() {
        val response = post(
            "{ contextInCoroutine contextAfterIo shelf(id: \"s1\") { requestOf sellers } }", "request-1"
        )
        assertNull(response["errors"], response.toString())
        assertEquals("request-1", response.data()["contextInCoroutine"])
        assertEquals("request-1", response.data()["contextAfterIo"])
        assertEquals("request-1", response.data().obj("shelf")["requestOf"])
    }

    @Test
    fun `a coroutine that switched to the IO dispatcher keeps the data and runs on its thread`() {
        val response = post("{ threadAfterIo }")
        assertNull(response["errors"], response.toString())
        assertTrue((response.data()["threadAfterIo"] as String).startsWith("DefaultDispatcher-worker"), response.toString())
    }

    @Test
    fun `an exception in the coroutine becomes an error of the field`() {
        val response = post("{ shelf(id: \"s1\") { id } failing }")
        @Suppress("UNCHECKED_CAST")
        val errors = response["errors"] as List<Map<String, Any?>>
        assertEquals(1, errors.size, response.toString())
        assertEquals(listOf("failing"), errors[0]["path"])
        assertNull(response["data"], response.toString())
    }

    @Test
    fun `cancelling the stage of a field cancels its coroutine`() {
        val method = context.getBeanDefinition(ShelfQueries::class.java).findMethod<Any>("cancelled", Continuation::class.java).get()
        val invoker = context.getBean(SuspendInvoker::class.java)
        @Suppress("UNCHECKED_CAST")
        val stage = invoker.invoke(
            method as io.micronaut.core.type.Executable<Any, *>, context.getBean(ShelfQueries::class.java), arrayOfNulls(1)
        ).toCompletableFuture()
        assertTrue(SuspendProbe.started.await(10, TimeUnit.SECONDS))
        assertFalse(stage.isDone)
        assertTrue(stage.cancel(true))
        assertTrue(SuspendProbe.finished.await(10, TimeUnit.SECONDS))
        assertEquals("cancelled", SuspendProbe.outcome.get())
    }
}
