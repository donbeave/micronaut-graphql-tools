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

import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvocationLimitsTest {

    private val port = mapOf<String, Any>("micronaut.server.port" to -1)

    @Test
    fun `an invocation that is only secondary fails the startup with the way out`() {
        val failure = assertThrows(Throwable::class.java) { ApplicationContext.run(port, LIMITS_SECONDARY) }

        val message = configuration(failure).message!!
        assertTrue(message.contains("@Secondary"), message)
        assertTrue(message.contains("Fix: remove @Secondary"), message)
    }

    @Test
    fun `a schema template over every invocation fails the startup with the way out`() {
        val failure = assertThrows(Throwable::class.java) { ApplicationContext.run(port, LIMITS_EACH_INVOCATION) }

        val message = configuration(failure).message!!
        assertTrue(message.contains("@EachBean(GraphQLInvocation.class)"), message)
        assertTrue(message.contains("target the application's own bean type"), message)
    }

    @Test
    fun `a factory default replaced by qualifier does not disable the library invocation`() {
        ApplicationContext.run(port + ("micronaut.graphql.tools.eager-init" to false), LIMITS_QUALIFIED_DEFAULT).use { context ->
            val types = context.getBeanDefinitions(GraphQLInvocation::class.java).map { it.beanType.simpleName }.toSet()

            assertTrue(types.contains("LibraryGraphQLInvocation"), types.toString())
            assertFalse(types.contains("DefaultGraphQLInvocation"), types.toString())
        }
    }

    private fun configuration(failure: Throwable): ConfigurationException {
        var current: Throwable? = failure
        while (current != null) {
            if (current is ConfigurationException) {
                return current
            }
            current = current.cause
        }
        throw AssertionError("no ConfigurationException", failure)
    }

}
