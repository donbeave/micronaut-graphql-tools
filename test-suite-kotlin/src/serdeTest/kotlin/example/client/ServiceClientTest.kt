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
package example.client

import example.client.ServiceClient.Viewer
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceClientTest : TestPropertyProvider {

    @Inject
    lateinit var client: ServiceClient

    @Inject
    lateinit var exampleClient: ExampleClient

    @Inject
    lateinit var recorder: RequestRecorder

    // tag::discovery[]
    override fun getProperties(): Map<String, String> {
        val port = SocketUtils.findAvailableTcpPort()
        return mapOf(
            "micronaut.server.port" to port.toString(),
            "micronaut.http.services.example.urls" to "http://127.0.0.1:$port"
        )
    }
    // end::discovery[]

    @Test
    fun aHeaderParameterIsSent() = runBlocking {
        // tag::headers[]
        val viewer = client.viewer("bob")

        assertEquals(Viewer("bob"), viewer)
        assertEquals("bob", recorder.header("X-User"))
        // end::headers[]
        assertNull(client.viewer(null))
        assertNull(recorder.header("X-User"))
    }

    @Test
    fun aCookieParameterIsSent() = runBlocking {
        // tag::cookie[]
        client.viewerOfSession("abc")

        assertEquals("session=abc", recorder.header("Cookie"))
        // end::cookie[]
    }

    @Test
    fun theFilterOfTheServiceIdRunsOnTheCallsOfThatService() = runBlocking {
        // tag::filter[]
        client.viewer("bob")
        assertEquals("example", recorder.header("X-Service-Filter"))

        exampleClient.introduced()
        assertNull(recorder.header("X-Service-Filter"))
        // end::filter[]
    }

    @Test
    fun aQueryIsRetried() = runBlocking {
        // tag::retry[]
        val before = recorder.requests()
        recorder.failNext(2)

        assertEquals(listOf(Viewer("bob")), client.profiles("bob"))
        assertEquals(before + 3, recorder.requests())
        // end::retry[]

        recorder.failNext(1)
        assertThrows(GraphQLTransportException::class.java) { runBlocking { client.profilesOnce("bob") } }
        assertEquals(listOf(Viewer("bob")), client.profilesOnce("bob"))
    }
}
