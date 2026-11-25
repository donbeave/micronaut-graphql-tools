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
package example.manifest

import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.manifest.BindingManifest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BindingManifestEndpointTest {

    @Test
    fun `the endpoint serves the manifest as json`() {
        run(mapOf("micronaut.graphql.tools.manifest.enabled" to true, "endpoints.graphql-binding.enabled" to true,
            "endpoints.graphql-binding.sensitive" to false)).use { server ->
            client(server).use { client ->
                val body = client.toBlocking().retrieve(HttpRequest.GET<Any>("/graphql-binding"), Map::class.java)
                val manifest = server.applicationContext.getBean(BindingManifest::class.java)

                assertEquals(manifest.header, body["header"])
                assertEquals(manifest.lines, body["lines"])
                assertTrue((body["lines"] as List<*>).any { it.toString().startsWith("Query.") })
            }
        }
    }

    @Test
    fun `the standard enabled key switches the endpoint off`() {
        run(mapOf("micronaut.graphql.tools.manifest.enabled" to true, "endpoints.graphql-binding.enabled" to false,
            "endpoints.graphql-binding.sensitive" to false)).use { server ->
            client(server).use { client ->
                val e = assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(HttpRequest.GET<Any>("/graphql-binding"), String::class.java)
                }
                assertEquals(HttpStatus.NOT_FOUND, e.status)
            }
        }
    }

    @Test
    fun `the standard sensitive key decides whether the endpoint needs authentication`() {
        val secured = mapOf("micronaut.graphql.tools.manifest.enabled" to true, "endpoints.graphql-binding.enabled" to true, "micronaut.security.enabled" to true,
            "endpoints.graphql-binding.sensitive" to true)
        run(secured).use { server ->
            client(server).use { client ->
                val e = assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(HttpRequest.GET<Any>("/graphql-binding"), String::class.java)
                }
                assertEquals(HttpStatus.UNAUTHORIZED, e.status)
            }
        }
        val open = secured + ("endpoints.graphql-binding.sensitive" to false)
        run(open).use { server ->
            client(server).use { client ->
                assertEquals(HttpStatus.OK, client.toBlocking().exchange(HttpRequest.GET<Any>("/graphql-binding"), String::class.java).status)
            }
        }
    }

    @Test
    fun `the endpoint is off until it is enabled explicitly`() {
        run(mapOf("micronaut.graphql.tools.manifest.enabled" to true, "endpoints.graphql-binding.sensitive" to false)).use { server ->
            client(server).use { client ->
                val e = assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(HttpRequest.GET<Any>("/graphql-binding"), String::class.java)
                }
                assertEquals(HttpStatus.NOT_FOUND, e.status)
            }
        }
    }

    @Test
    fun `without the recorded manifest no endpoint is registered`() {
        run(mapOf("micronaut.graphql.tools.manifest.enabled" to false)).use { server ->
            client(server).use { client ->
                val e = assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().exchange(HttpRequest.GET<Any>("/graphql-binding"), String::class.java)
                }
                assertEquals(HttpStatus.NOT_FOUND, e.status)
            }
        }
    }

    private fun run(properties: Map<String, Any>): EmbeddedServer = ApplicationContext.run(
        EmbeddedServer::class.java,
        mapOf<String, Any>(
            "micronaut.server.port" to -1,
            "micronaut.server.host" to "127.0.0.1",
            "micronaut.metrics.binders.executor.enabled" to false,
            "micronaut.security.enabled" to false,
        ) + properties,
    )

    private fun client(server: EmbeddedServer): HttpClient = server.applicationContext.createBean(HttpClient::class.java, server.url)
}
