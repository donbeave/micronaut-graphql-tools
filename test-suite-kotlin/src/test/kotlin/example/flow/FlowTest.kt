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
package example.flow

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import graphql.schema.GraphQLSchema
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

// Runs on the JVM and as a native binary (nativeTest): Kotlin Flow subscriptions over graphql-transport-ws.
class FlowTest {

    private class Messages : WebSocket.Listener {
        private val queue = LinkedBlockingQueue<String>()
        private val partial = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            partial.append(data)
            if (last) {
                queue.add(partial.toString())
                partial.setLength(0)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            queue.add("close $statusCode")
            return null
        }

        fun next(): String {
            while (true) {
                val message = queue.poll(10, TimeUnit.SECONDS)
                assertNotNull(message, "no message in 10 seconds")
                if (!message.contains("\"ping\"") && !message.contains("\"pong\"")) {
                    return message
                }
            }
        }
    }

    private fun session(headers: Map<String, String>, test: (WebSocket, Messages) -> Unit) {
        val properties = mapOf<String, Any>(
            "graphql.graphql-ws.enabled" to true,
            "micronaut.server.host" to "127.0.0.1",
            "micronaut.server.port" to -1
        )
        ApplicationContext.builder().environments(FLOW_SUBSCRIPTIONS).properties(properties).start().use { context ->
            val server = context.getBean(EmbeddedServer::class.java).start()
            val messages = Messages()
            val builder = HttpClient.newHttpClient().newWebSocketBuilder().subprotocols("graphql-transport-ws")
            headers.forEach { (name, value) -> builder.header(name, value) }
            val socket = builder.buildAsync(URI.create("ws://${server.host}:${server.port}/graphql-ws"), messages)
                .get(10, TimeUnit.SECONDS)
            socket.sendText("{\"type\":\"connection_init\"}", true).get(10, TimeUnit.SECONDS)
            assertTrue(messages.next().contains("connection_ack"))
            test(socket, messages)
            socket.abort()
        }
    }

    private fun subscribe(socket: WebSocket, id: String, query: String) {
        socket.sendText("{\"id\":\"$id\",\"type\":\"subscribe\",\"payload\":{\"query\":\"$query\"}}", true)
            .get(10, TimeUnit.SECONDS)
    }

    @Test
    @Timeout(60)
    fun `a Flow subscription delivers its events and completes`() {
        session(emptyMap()) { socket, messages ->
            subscribe(socket, "1", "subscription { readings(count: 3) { number } }")
            for (number in 0..2) {
                val next = messages.next()
                assertTrue(next.contains("\"type\":\"next\"") && next.contains("\"number\":$number"), next)
            }
            val complete = messages.next()
            assertTrue(complete.contains("\"type\":\"complete\"") && complete.contains("\"id\":\"1\""), complete)
        }
    }

    @Test
    @Timeout(60)
    fun `the events of a Flow are resolved off the event loop with the context of the connection`() {
        session(mapOf("X-Flow" to "hello")) { socket, messages ->
            subscribe(socket, "1", "subscription { readings(count: 2) { sourceNonBlocking onEventLoop requestHeader } }")
            for (i in 0..1) {
                val next = messages.next()
                assertTrue(next.contains("\"sourceNonBlocking\":true"), "the source emits on a non-blocking thread: $next")
                assertTrue(next.contains("\"onEventLoop\":false"), next)
                assertTrue(next.contains("\"requestHeader\":\"hello\""), next)
            }
            assertTrue(messages.next().contains("\"type\":\"complete\""))
        }
    }

    @Test
    @Timeout(60)
    fun `a client that closes the connection cancels the flow`() {
        session(emptyMap()) { socket, messages ->
            subscribe(socket, "1", "subscription { endless { number } }")
            assertTrue(messages.next().contains("\"type\":\"next\""))
            assertEquals(1, FlowProbe.cancelled.count)
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS)
            assertTrue(FlowProbe.cancelled.await(10, TimeUnit.SECONDS), "the flow was not cancelled")
        }
    }

    @Test
    fun `a Flow on a query field fails the startup with a clear message`() {
        var e: Throwable = assertThrows(Exception::class.java) {
            ApplicationContext.run(mapOf<String, Any>(), FLOW_ON_QUERY).use { context -> context.getBean(GraphQLSchema::class.java) }
        }
        while (e !is SchemaBindingException && e.cause != null && e.cause !== e) {
            e = e.cause!!
        }
        val message = e.message!!
        assertTrue(e is SchemaBindingException && message.contains("Query.flowOnQuery") && message.contains("Kotlin Flow") &&
            message.contains("subscription type") && message.contains("Fix:"), message)
    }
}
