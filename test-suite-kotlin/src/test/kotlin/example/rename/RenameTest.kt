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
package example.rename

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): a field bound to a member with another name.
class RenameTest {

    private val prefixes = "micronaut.graphql.tools.resolver-accessor-prefixes"

    private fun run(properties: Map<String, Any>, environment: String, query: String): Any? =
        ApplicationContext.run(properties, environment).use { context ->
            val schema = context.getBean(GraphQLSchema::class.java)
            val result = GraphQL.newGraphQL(schema).build().execute(query)
            assertTrue(result.errors.isEmpty(), result.errors.toString())
            result.getData<Any>()
        }

    private fun failure(properties: Map<String, Any>, environment: String): SchemaBindingException {
        var e: Throwable = assertThrows(Exception::class.java) { run(properties, environment, "{ __typename }") }
        while (e !is SchemaBindingException && e.cause != null && e.cause !== e) {
            e = e.cause!!
        }
        assertTrue(e is SchemaBindingException, e.toString())
        return e as SchemaBindingException
    }

    @Test
    fun `a field named like a keyword binds to a member with another name`() {
        assertEquals(
            mapOf(
                "parcel" to mapOf(
                    "id" to "1", "class" to "parcel", "hashCode" to 42, "headline" to "Fragile",
                    "tagline" to "Handle with care", "new" to "a parcel"
                )
            ),
            run(mapOf(), RENAME_MEMBERS, "{ parcel { id class hashCode headline tagline new(prefix: \"a\") } }")
        )
    }

    @Test
    fun `a renamed member and another member for one field fail the startup`() {
        val e = failure(mapOf(), RENAME_CLASH)
        assertTrue(e.message!!.startsWith("[multiple-methods-found]"), e.message)
        assertTrue(e.message!!.contains("GraphQL coordinate: Slip.title"), e.message)
    }

    @Test
    fun `a coordinate binds a resolver method with another name`() {
        assertEquals(
            mapOf(
                "default" to "plain",
                "parcel" to mapOf(
                    "id" to "1", "class" to "parcel", "hashCode" to 42, "headline" to "Fragile",
                    "tagline" to "Handle with care", "new" to "a parcel"
                )
            ),
            run(mapOf(), RENAME_COORDINATES, "{ default parcel { id class hashCode headline tagline new(prefix: \"a\") } }")
        )
    }

    @Test
    fun `the accessor prefixes bind getX and isX resolver methods when they are on`() {
        assertEquals(
            mapOf("stamp" to "stamped", "parcel" to mapOf("id" to "1", "author" to "Jane", "active" to true)),
            run(mapOf(prefixes to true), RENAME_PREFIXES, "{ stamp parcel { id author active } }")
        )
        val off = failure(mapOf(), RENAME_PREFIXES)
        assertTrue(off.message!!.contains("[field-not-bound]"), off.message)
    }
}
