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
package example.executeon

import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): the startup failures of @ExecuteOn.
class ExecuteOnFailureTest {

    private fun failure(environment: String): SchemaBindingException {
        var e: Throwable = assertThrows(Exception::class.java) {
            ApplicationContext.run(mapOf<String, Any>(), environment).use { context -> context.getBean(GraphQLSchema::class.java) }
        }
        while (e !is SchemaBindingException && e.cause != null && e.cause !== e) {
            e = e.cause!!
        }
        assertTrue(e is SchemaBindingException, e.toString())
        return e as SchemaBindingException
    }

    @Test
    fun `an unknown executor fails the startup and lists the executor beans`() {
        val e = failure(EXECUTE_ON_UNKNOWN)
        val message = e.message!!
        assertEquals(BindingCode.UNKNOWN_EXECUTOR, e.code())
        assertTrue(message.startsWith("[unknown-executor] ") && message.contains("@ExecuteOn(\"no-such-executor\")") &&
            message.contains("Query.slowLookup"), message)
        assertTrue(message.contains("The executor beans are:") && message.contains("io"), message)
        assertTrue(message.contains("micronaut.executors"), message)
    }

    @Test
    fun `@ExecuteOn on a property fails the startup and says how to fix it`() {
        val e = failure(EXECUTE_ON_PROPERTY)
        val message = e.message!!
        assertEquals(BindingCode.EXECUTE_ON_PROPERTY, e.code())
        assertTrue(message.startsWith("[execute-on-property] "), message)
        assertTrue(message.contains("@GraphQLField method") && message.contains("type resolver"), message)
    }
}
