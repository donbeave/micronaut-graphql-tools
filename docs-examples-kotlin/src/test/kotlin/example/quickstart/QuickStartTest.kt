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
package example.quickstart

import graphql.GraphQL
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class QuickStartTest {

    // tag::query[]
    @Test
    fun theFirstQueryReturnsTheBooks() {
        ApplicationContext.run().use { context ->
            val graphQL = context.getBean(GraphQL::class.java)

            val result = graphQL.execute("{ allBooks { id title } }")

            assertTrue(result.errors.isEmpty())
            val data: Map<String, List<Map<String, Any>>> = result.getData()!!
            assertEquals("The Fractured Sage", data["allBooks"]!!.first()["title"])
        }
    }
    // end::query[]

    @Test
    fun theSameQueryOverHttpReturnsTheResponseOfTheGuide() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("micronaut.server.port" to -1)).use { server ->
            val request = HttpRequest.newBuilder(URI.create(server.url.toString() + "/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"query":"{ allBooks { id title } }"}"""))
                .build()

            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

            assertEquals(200, response.statusCode())
            assertEquals(
                """{"data":{"allBooks":[{"id":"ab","title":"The Fractured Sage"},{"id":"ax","title":"Considered for Duty"}]}}""",
                response.body()
            )
        }
    }

}
