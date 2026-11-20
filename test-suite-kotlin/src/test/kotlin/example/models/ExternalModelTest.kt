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
package example.models

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): classes without an annotation bind through the introspections that
// @GraphQLModels generates, and the image needs no other metadata for them.
class ExternalModelTest {

    private fun run(environment: String, query: String): Any? =
        ApplicationContext.run(mapOf<String, Any>(), environment).use { context ->
            val schema = context.getBean(GraphQLSchema::class.java)
            val result = GraphQL.newGraphQL(schema).build().execute(query)
            assertTrue(result.errors.isEmpty(), result.errors.toString())
            result.getData<Any>()
        }

    private fun failure(environment: String): SchemaBindingException {
        var e: Throwable = assertThrows(Exception::class.java) { run(environment, "{ __typename }") }
        while (e !is SchemaBindingException && e.cause != null && e.cause !== e) {
            e = e.cause!!
        }
        assertTrue(e is SchemaBindingException, e.toString())
        return e as SchemaBindingException
    }

    @Test
    fun `listed classes bind without any other annotation`() {
        assertEquals(
            mapOf(
                "atlas" to mapOf("title" to "World", "region" to mapOf("name" to "Europe"), "pages" to 320),
                "atlases" to listOf(mapOf("title" to "World"), mapOf("title" to "Roads"))
            ),
            run(EXTERNAL_CLASSES, "{ atlas { title region { name } pages } atlases { title } }")
        )
    }

    @Test
    fun `the classes of a listed package bind`() {
        assertEquals(
            mapOf("chart" to mapOf("label" to "Sales", "axis" to mapOf("unit" to "EUR"))),
            run(EXTERNAL_PACKAGE, "{ chart { label axis { unit } } }")
        )
    }

    @Test
    fun `a returned class that nothing lists fails the startup with the class and the field`() {
        val e = failure(EXTERNAL_MISSING)
        assertTrue(e.message!!.startsWith("[class-not-introspected]"), e.message)
        assertTrue(e.message!!.contains("example.external.unlisted.Unlisted"), e.message)
        assertTrue(e.message!!.contains("GraphQL coordinate: Query.unlisted"), e.message)
        assertTrue(e.message!!.contains("@GraphQLModels"), e.message)
    }

    @Test
    fun `a class reached through a property that nothing lists fails the startup with the field`() {
        val e = failure(EXTERNAL_NESTED)
        assertTrue(e.message!!.startsWith("[class-not-introspected]"), e.message)
        assertTrue(e.message!!.contains("example.external.unlisted.Inner"), e.message)
        assertTrue(e.message!!.contains("Holder.inner"), e.message)
    }
}
