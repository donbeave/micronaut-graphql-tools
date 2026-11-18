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
package example.directives

import graphql.schema.GraphQLSchema
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@MicronautTest
class DirectiveWiringTest(
    @Client("/") private val client: HttpClient,
    private val schema: GraphQLSchema,
) {

    @Suppress("UNCHECKED_CAST")
    private fun data(query: String): Map<String, Any?> {
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/graphql", mapOf("query" to query)),
            Argument.mapOf(Argument.STRING, Argument.OBJECT_ARGUMENT),
        )
        assertNull(response["errors"], "GraphQL errors: ${response["errors"]}")
        return response["data"] as Map<String, Any?>
    }

    @Test
    fun aNamedSchemaDirectiveRunsOnTheFieldThatAppliesIt() {
        assertEquals("QUIET", data("{ shout }")["shout"])
    }

    @Test
    fun anEveryElementSchemaDirectiveRunsOnTheFieldThatAppliesIt() {
        assertEquals("[t] value", data("{ tagged }")["tagged"])
    }

    @Test
    fun aWiringDoesNotChangeAFieldThatDoesNotApplyItsDirective() {
        assertEquals("introduced introduced", data("{ introduced }")["introduced"])
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun aSchemaLevelDirectiveIsHandledByTheSchemaCustomizerThatItIsListedFor() {
        assertNotNull(schema.getSchemaAppliedDirective("audit"))
        assertEquals("audited", (data("{ __schema { description } }")["__schema"] as Map<String, Any?>)["description"])
    }
}
