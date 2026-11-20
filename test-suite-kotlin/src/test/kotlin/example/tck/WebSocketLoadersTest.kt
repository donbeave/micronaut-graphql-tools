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
package example.tck

import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.tck.Tck
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Two operations on one WebSocket connection never share a DataLoader cache: each operation gets its own registry, so the
 * batch function of the `data-loader` scenario runs once for each operation, not once for the connection.
 */
class WebSocketLoadersTest {

    private class Messages : WebSocket.Listener {
        private val queue = LinkedBlockingQueue<String>()
        private val partial = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            partial.append(data)
            if (last) {
                // the server sends keep-alive messages between the ones that matter
                if (!partial.contains("\"ka\"")) {
                    queue.add(partial.toString())
                }
                partial.setLength(0)
            }
            webSocket.request(1)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            queue.add("error $error")
        }

        fun next(): String {
            val message = queue.poll(10, TimeUnit.SECONDS)
            assertNotNull(message, "no message in 10 seconds")
            return message
        }
    }

    @Test
    @Timeout(60)
    fun twoOperationsOnOneConnectionLoadInTwoBatches() {
        val properties = LinkedHashMap<String, Any>(Tck.applicationProperties("data-loader"))
        properties["graphql.graphql-apollo-ws.enabled"] = true
        val client = HttpClient.newHttpClient()
        ApplicationContext.builder().properties(properties).start().use { context ->
            val server = context.getBean(EmbeddedServer::class.java).start()
            val authority = "${server.host}:${server.port}"
            val messages = Messages()
            val socket = client.newWebSocketBuilder().subprotocols("graphql-ws")
                .buildAsync(URI.create("ws://$authority/graphql-ws"), messages).get(10, TimeUnit.SECONDS)

            socket.sendText("{\"type\":\"connection_init\"}", true).get(10, TimeUnit.SECONDS)
            assertTrue(messages.next().contains("connection_ack"))

            for (id in listOf("1", "2")) {
                socket.sendText("{\"id\":\"$id\",\"type\":\"start\",\"payload\":{\"query\":\"$QUERY\"}}", true).get(10, TimeUnit.SECONDS)
                val data = messages.next()
                assertTrue(data.contains("\"data\"") && data.contains("grace-2"), data)
                assertTrue(messages.next().contains("complete"))
            }
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS)

            val stats = client.send(
                HttpRequest.newBuilder(URI.create("http://$authority/graphql"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ loaderStats { batches keys } }\"}")).build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertTrue(stats.body().contains("\"batches\":2") && stats.body().contains("\"keys\":6"), stats.body())
        }
    }

    private companion object {
        const val QUERY = "{ authors { name volumes { title } } }"
    }
}
