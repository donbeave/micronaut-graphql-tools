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
package example.fileupload

import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.upload.GraphQLUpload
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.json.JsonMapper
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): a real multipart request, from the client to the resolver.
class UploadTest {

    private val server = mapOf(
        "micronaut.server.host" to "127.0.0.1",
        "micronaut.server.port" to -1,
        "graphql.graphiql.enabled" to false
    )

    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)

    private fun start(): EmbeddedServer = ApplicationContext.run(EmbeddedServer::class.java, server, UPLOAD)

    private fun post(embedded: EmbeddedServer, body: MultipartBody): Map<*, *> {
        val context = embedded.applicationContext
        context.createBean(HttpClient::class.java, embedded.url).use { client ->
            val response = client.toBlocking()
                .retrieve(HttpRequest.POST("/graphql", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String::class.java)
            return context.getBean(JsonMapper::class.java).readValue(response, Map::class.java)
        }
    }

    // tag::top-level[]
    @Test
    fun aMutationReceivesTheUploadedFile() {
        start().use { embedded ->
            val body = MultipartBody.builder()
                .addPart(
                    "operations",
                    "{\"query\":\"mutation (\$file: Upload!) { uploadedFile(file: \$file) }\",\"variables\":{\"file\":null}}"
                )
                .addPart("map", "{\"0\":[\"variables.file\"]}")
                .addPart("0", "notes.txt", MediaType.TEXT_PLAIN_TYPE, bytes("hello"))
                .build()

            val response = post(embedded, body)

            assertNull(response["errors"])
            assertEquals(mapOf("uploadedFile" to "notes.txt:hello"), response["data"])
        }
    }
    // end::top-level[]

    // tag::input-object[]
    @Test
    fun aMutationReceivesFilesInsideAnInputObject() {
        start().use { embedded ->
            val body = MultipartBody.builder()
                .addPart(
                    "operations",
                    "{\"query\":\"mutation (\$input: BookUpload!) { uploadedBook(input: \$input) }\"," +
                        "\"variables\":{\"input\":{\"title\":\"Dune\",\"cover\":null,\"pages\":[null,null]}}}"
                )
                .addPart(
                    "map",
                    "{\"0\":[\"variables.input.cover\"],\"1\":[\"variables.input.pages.0\"],\"2\":[\"variables.input.pages.1\"]}"
                )
                .addPart("0", "cover.png", MediaType.IMAGE_PNG_TYPE, bytes("cover"))
                .addPart("1", "1.txt", MediaType.TEXT_PLAIN_TYPE, bytes("one"))
                .addPart("2", "2.txt", MediaType.TEXT_PLAIN_TYPE, bytes("two"))
                .build()

            val response = post(embedded, body)

            assertNull(response["errors"])
            assertEquals(mapOf("uploadedBook" to "Dune cover=cover page=one page=two"), response["data"])
        }
    }
    // end::input-object[]

    // tag::executor[]
    @Test
    fun theResolverRunsOffTheEventLoop() {
        start().use { embedded ->
            val body = MultipartBody.builder()
                .addPart(
                    "operations",
                    "{\"query\":\"mutation (\$file: Upload!) { uploadedOn(file: \$file) }\",\"variables\":{\"file\":null}}"
                )
                .addPart("map", "{\"0\":[\"variables.file\"]}")
                .addPart("0", "a.txt", MediaType.TEXT_PLAIN_TYPE, bytes("x"))
                .build()

            val thread = (post(embedded, body)["data"] as Map<*, *>)["uploadedOn"].toString()

            assertFalse(thread.contains("event-loop"), thread)
            assertFalse(thread.contains("nioEventLoopGroup"), thread)
        }
    }
    // end::executor[]

    // tag::invalid[]
    @Test
    fun aMapThatNamesAMissingPartIsAnErrorWithACode() {
        start().use { embedded ->
            val body = MultipartBody.builder()
                .addPart(
                    "operations",
                    "{\"query\":\"mutation (\$file: Upload!) { uploadedFile(file: \$file) }\",\"variables\":{\"file\":null}}"
                )
                .addPart("map", "{\"0\":[\"variables.file\"]}")
                .build()

            val failure = assertThrows(HttpClientResponseException::class.java) { post(embedded, body) }

            assertEquals(HttpStatus.BAD_REQUEST, failure.status)
            val parsed = embedded.applicationContext.getBean(JsonMapper::class.java)
                .readValue(failure.response.getBody(String::class.java).orElseThrow(), Map::class.java)
            val error = (parsed["errors"] as List<*>)[0] as Map<*, *>
            assertEquals(GraphQLUpload.INVALID_REQUEST_CODE, (error["extensions"] as Map<*, *>)["code"])
            assertTrue(error["message"].toString().contains("no such part"))
        }
    }
    // end::invalid[]

    // tag::precedence[]
    @Test
    fun aJsonRequestOnTheSamePathStillReachesTheTransportRoute() {
        start().use { embedded ->
            embedded.applicationContext.createBean(HttpClient::class.java, embedded.url).use { client ->
                val response = client.toBlocking().retrieve(
                    HttpRequest.POST("/graphql", "{\"query\":\"{ __typename }\"}").contentType(MediaType.APPLICATION_JSON_TYPE),
                    String::class.java
                )

                assertEquals(
                    mapOf("data" to mapOf("__typename" to "Query")),
                    embedded.applicationContext.getBean(JsonMapper::class.java).readValue(response, Map::class.java)
                )
            }
        }
    }
    // end::precedence[]
}
