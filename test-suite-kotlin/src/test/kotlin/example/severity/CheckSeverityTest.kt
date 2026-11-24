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
package example.severity

import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

const val CHECK_SEVERITY_ENVIRONMENT = "check-severity"

// A type that no field returns, which the unreachable-type check finds
@Singleton
@Requires(env = [CHECK_SEVERITY_ENVIRONMENT])
class SeveritySchema : TypeDefinitionRegistryCustomizer {
    override fun customize(registry: TypeDefinitionRegistry) {
        registry.merge(SchemaParser().parse("type Orphan { id: ID }\n"))
    }
}

// Runs on the JVM and as a native binary (nativeTest): the schema has a type that no field returns, and the severity
// settings decide whether the startup fails.
class CheckSeverityTest {

    private val prefix = "micronaut.graphql.tools."

    private fun start(properties: Map<String, Any>): GraphQLSchema =
        ApplicationContext.run(properties, CHECK_SEVERITY_ENVIRONMENT).use { it.getBean(GraphQLSchema::class.java) }

    private fun assertFails(properties: Map<String, Any>, code: String): SchemaBindingException {
        var e: Throwable = assertThrows(Exception::class.java) { start(properties) }
        while (e !is SchemaBindingException && e.cause != null && e.cause !== e) {
            e = e.cause!!
        }
        assertTrue(e is SchemaBindingException, e.toString())
        assertTrue(e.message!!.startsWith("[$code]"), e.message)
        return e as SchemaBindingException
    }

    @Test
    fun `an unreachable type fails the startup by default`() {
        val e = assertFails(mapOf(), "unreachable-type")
        // the failure is about a type, not one field: no coordinate, and it is no aggregate
        assertEquals(BindingCode.UNREACHABLE_TYPE, e.getCode())
        assertNull(e.coordinate)
        assertTrue(e.failures.isEmpty())
        assertTrue(e.codes.isEmpty())
    }

    @Test
    fun `warn, ignore and strict false let the startup go on`() {
        assertNotNull(start(mapOf(prefix + "checks.unreachable-type" to "warn")))
        assertNotNull(start(mapOf(prefix + "checks.unreachable-type" to "ignore")))
        assertNotNull(start(mapOf(prefix + "strict" to false)))
    }

    @Test
    fun `an explicit fail wins over strict false`() {
        assertFails(mapOf(prefix + "strict" to false, prefix + "checks.unreachable-type" to "fail"), "unreachable-type")
    }

    @Test
    fun `an allowed type and an allowed manifest line accept the finding`() {
        assertNotNull(start(mapOf(prefix + "allow.unreachable" to listOf("Orphan"))))
        assertNotNull(start(mapOf(prefix + "allow.findings" to listOf("!unreachable-type Orphan"))))
    }

    @Test
    fun `a stale allow entry fails the startup`() {
        assertFails(
            mapOf(prefix + "allow.unreachable" to listOf("Orphan"),
                prefix + "allow.findings" to listOf("!unreachable-type Missing")), "invalid-allow-findings")
    }

    @Test
    fun `a removed key and an unknown check fail the startup`() {
        assertFails(mapOf(prefix + "unreachable-types" to "ignore"), "removed-configuration-key")
        assertFails(mapOf(prefix + "checks.no-such-check" to "warn"), "invalid-check-setting")
    }

    @Test
    fun `the severity accepts the spellings of the enum conversion`() {
        assertNotNull(start(mapOf(prefix + "checks.unreachable-type" to "WARN")))
        assertNotNull(start(mapOf(prefix + "checks.unreachable-type" to "Ignore")))
    }

    @Test
    fun `an invalid severity fails the startup with the property and the valid values`() {
        val e = assertFails(mapOf(prefix + "checks.unreachable-type" to "sometimes"), "invalid-check-setting")
        assertTrue(e.message!!.contains(prefix + "checks.unreachable-type"), e.message)
        assertTrue(e.message!!.contains("fail, warn, ignore"), e.message)
    }
}
