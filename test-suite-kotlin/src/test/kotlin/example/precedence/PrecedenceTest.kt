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
package example.precedence

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): the precedence rule for a field that a property and a type
// resolver method can both bind.
class PrecedenceTest {

    private val precedence = "micronaut.graphql.tools.precedence"

    private fun run(properties: Map<String, Any>, environment: String, query: String): Any? =
        ApplicationContext.run(properties, environment).use { context ->
            val schema = context.getBean(GraphQLSchema::class.java)
            val result = GraphQL.newGraphQL(schema).build().execute(query)
            assertTrue(result.errors.isEmpty(), result.errors.toString())
            result.getData<Any>()
        }

    private fun assertFails(properties: Map<String, Any>, environment: String, code: String): SchemaBindingException {
        var e: Throwable = assertThrows(Exception::class.java) { run(properties, environment, "{ __typename }") }
        while (e !is SchemaBindingException && e.cause != null && e.cause !== e) {
            e = e.cause!!
        }
        assertTrue(e is SchemaBindingException, e.toString())
        assertTrue(e.message!!.startsWith("[$code]"), e.message)
        return e as SchemaBindingException
    }

    @Test
    fun `a resolver that declares its coordinate binds the field next to a property`() {
        assertEquals(
            mapOf("gizmo" to mapOf("id" to "global", "name" to "cog")),
            run(mapOf(), PRECEDENCE_RELAY, "{ gizmo { id ... on Sprocket { name } } }")
        )
    }

    @Test
    fun `an ambiguous field fails the startup by default and strict false does not change it`() {
        val e = assertFails(mapOf(), PRECEDENCE_AMBIGUOUS, "precedence-ambiguity")
        // a failure about one field names its schema coordinate and is no aggregate
        assertEquals(BindingCode.PRECEDENCE_AMBIGUITY, e.code())
        assertEquals("Gadget.label", e.coordinate)
        assertEquals("Gadget.label", e.mappingContext.coordinate)
        assertTrue(e.failures.isEmpty())
        assertTrue(e.codes.isEmpty())
        assertFails(mapOf("micronaut.graphql.tools.strict" to false), PRECEDENCE_AMBIGUOUS, "precedence-ambiguity")
    }

    @Test
    fun `the lenient settings decide the field`() {
        assertEquals(
            mapOf("gadget" to mapOf("label" to "resolved pen")),
            run(mapOf(precedence to "resolver-first"), PRECEDENCE_AMBIGUOUS, "{ gadget { label } }")
        )
        assertEquals(
            mapOf("gadget" to mapOf("label" to "own")),
            run(mapOf(precedence to "property-first"), PRECEDENCE_AMBIGUOUS, "{ gadget { label } }")
        )
    }

    @Test
    fun `the removed value strict fails the startup`() {
        assertFails(mapOf(precedence to "strict"), PRECEDENCE_RELAY, "removed-configuration-key")
    }
}
