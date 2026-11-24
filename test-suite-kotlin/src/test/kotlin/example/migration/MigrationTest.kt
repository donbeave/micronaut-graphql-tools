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
package example.migration

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.manifest.BindingManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): the mappings that the migration page lists for resolvers written
// for graphql-java-tools.
class MigrationTest {

    private fun execute(properties: Map<String, Any>, environment: String, query: String): ExecutionResult =
        ApplicationContext.run(properties, environment).use { context ->
            val schema = context.getBean(GraphQLSchema::class.java)
            GraphQL.newGraphQL(schema).build().execute(query)
        }

    private fun data(properties: Map<String, Any>, environment: String, query: String): Map<String, Any?> {
        val result = execute(properties, environment, query)
        assertTrue(result.errors.isEmpty(), result.errors.toString())
        return result.getData()
    }

    private fun failureCode(properties: Map<String, Any>, environment: String): String {
        var e: Throwable = assertThrows(Exception::class.java) { execute(properties, environment, "{ __typename }") }
        while (e !is SchemaBindingException && e.cause != null && e.cause !== e) {
            e = e.cause!!
        }
        assertTrue(e is SchemaBindingException, e.toString())
        val message = e.message!!
        return message.substring(1, message.indexOf(']'))
    }

    @Test
    fun `root resolvers bind by method name and the model by its properties`() {
        val data = data(
            mapOf(), MIGRATION,
            "{ note(id: \"1\") { id title done summary } missing: note(id: \"9\") { id } }"
        )
        assertEquals(mapOf("id" to "1", "title" to "first", "done" to false, "summary" to "first"), data["note"])
        assertNull(data["missing"])
    }

    @Test
    fun `a type resolver method binds a field the model does not have`() {
        assertEquals(
            mapOf("addNote" to mapOf("summary" to "third")),
            data(mapOf(), MIGRATION, "mutation { addNote(title: \"third\") { summary } }")
        )
    }

    @Test
    fun `arguments bind by name not by position`() {
        // the schema declares (tag, limit) and the method (limit, tag)
        assertEquals(
            mapOf("notes" to listOf(mapOf("id" to "2"))),
            data(mapOf(), MIGRATION, "{ notes(tag: \"sec\", limit: 5) { id } }")
        )
        assertEquals(
            mapOf("notes" to listOf(mapOf("id" to "1"))),
            data(mapOf(), MIGRATION, "{ notes(limit: 1) { id } }")
        )
    }

    @Test
    fun `a method named getX binds a field only with resolver accessor prefixes`() {
        assertEquals("field-not-bound", failureCode(mapOf(), PREFIXES))
        assertEquals(
            mapOf("label" to mapOf("text" to "note", "shout" to "NOTE")),
            data(mapOf("micronaut.graphql.tools.resolver-accessor-prefixes" to true), PREFIXES, "{ label { text shout } }")
        )
    }

    @Test
    fun `a field with an underscore needs an explicit name`() {
        assertEquals("field-not-bound", failureCode(mapOf(), SNAKE_PLAIN))
        assertEquals(
            mapOf("stamp" to mapOf("created_at" to "today")),
            data(mapOf(), SNAKE, "{ stamp { created_at } }")
        )
    }

    @Test
    fun `an Optional argument is refused at startup`() {
        assertEquals("incorrect-class-mapping", failureCode(mapOf(), OPTIONAL_ARGUMENT))
    }

    @Test
    fun `a Map is not the source of an object type`() {
        assertEquals("implementation-not-found", failureCode(mapOf(), MAP))
    }

    // tag::recipe[]
    @Test
    fun `the migration settings turn a failure into a finding in the manifest`() {
        assertEquals("unreachable-type", failureCode(mapOf(), FINDINGS))

        val settings = mapOf(
            "micronaut.graphql.tools.strict" to false,
            "micronaut.graphql.tools.precedence" to "resolver-first",
            "micronaut.graphql.tools.manifest.enabled" to true
        )
        ApplicationContext.run(settings, FINDINGS).use { context ->
            context.getBean(GraphQLSchema::class.java)

            val lines = context.getBean(BindingManifest::class.java).lines
            assertTrue(lines.any { it.startsWith("!unreachable-type ") }, lines.toString())
        }
    }
    // end::recipe[]
}
