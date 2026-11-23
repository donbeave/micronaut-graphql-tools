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
package example.replaced

import graphql.schema.GraphQLSchema
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReplacedBeansTest {

    private val port = mapOf<String, Any>("micronaut.server.port" to -1)

    @Test
    fun `an application schema serves the request`() {
        ApplicationContext.run(EmbeddedServer::class.java, port, REPLACED_SCHEMA).use { server ->
            val context = server.applicationContext

            assertEquals(1, context.getBeansOfType(GraphQLSchema::class.java).size)
            assertNotNull(context.getBean(GraphQLSchema::class.java).queryType.getFieldDefinition("owner"))
            assertTrue(query(context, server).contains("application schema"))
        }
    }

    // the example resolvers need a DataLoader, so an application invocation comes with an application schema
    @Test
    fun `an application invocation serves the request`() {
        ApplicationContext.run(EmbeddedServer::class.java, port, REPLACED_SCHEMA, REPLACED_INVOCATION).use { server ->
            val context = server.applicationContext

            assertTrue(context.getBean(GraphQLInvocation::class.java).javaClass.name.contains("ReplacedBeansFactory"))
            assertTrue(query(context, server).contains("application invocation"))
        }
    }

    private fun query(context: ApplicationContext, server: EmbeddedServer): String =
        context.createBean(HttpClient::class.java, server.url).use { client ->
            client.toBlocking().retrieve(
                HttpRequest.POST("/graphql", "{\"query\":\"{ owner }\"}").contentType(MediaType.APPLICATION_JSON),
                String::class.java
            )
        }

}
