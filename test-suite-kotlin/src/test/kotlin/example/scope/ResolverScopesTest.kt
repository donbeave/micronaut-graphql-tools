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
package example.scope

import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import jakarta.inject.Inject

@MicronautTest(environments = [SCOPES_ENVIRONMENT])
class ResolverScopesTest(@Client("/") private val client: HttpClient) {

    @Inject
    lateinit var context: ApplicationContext

    @Inject
    lateinit var schema: GraphQLSchema

    @Suppress("UNCHECKED_CAST")
    private fun execute(query: String): Map<String, Int> {
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/graphql", mapOf("query" to query)),
            Argument.mapOf(Argument.STRING, Argument.OBJECT_ARGUMENT),
        )
        assertNull(response["errors"], "GraphQL errors: ${response["errors"]}")
        return response["data"] as Map<String, Int>
    }

    private fun id(field: String): Int = execute("{ $field }").getValue(field)

    @Test
    fun aSingletonResolverIsOneInstanceAndAPrototypeResolverIsANewInstancePerCall() {
        assertEquals(id("singletonId"), id("singletonId"))
        assertNotEquals(id("prototypeId"), id("prototypeId"))
    }

    @Test
    fun aRequestScopedResolverIsOneInstancePerHttpRequest() {
        val twoCalls = execute("{ a: requestId b: requestId }")

        assertEquals(twoCalls["a"], twoCalls["b"])
        assertNotEquals(id("requestId"), id("requestId"))
    }

    @Test
    fun aSecondSchemaBuildBindsTheSameFieldsAsTheFirst() {
        val second = context.createBean(GraphQLSchema::class.java)

        assertNotSame(schema, second)
        assertNotNull(second.queryType.getFieldDefinition("prototypeId"))
        assertEquals(schema.queryType.fieldDefinitions.size, second.queryType.fieldDefinitions.size)
    }
}
