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
package example.eager

import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EagerBindingTest {

    private val port = mapOf<String, Any>("micronaut.server.port" to -1)

    @Test
    fun `a valid schema starts the server with the schema already bound`() {
        ApplicationContext.run(EmbeddedServer::class.java, port).use { server ->
            assertNotNull(
                server.applicationContext.getBean(GraphQLSchema::class.java).queryType.getFieldDefinition("allBooks")
            )
        }
    }

    @Test
    fun `a broken binding fails the server start`() {
        val e = assertThrows(BeanInstantiationException::class.java) {
            ApplicationContext.run(EmbeddedServer::class.java, port, BROKEN_BINDING)
        }

        assertTrue(rootCause(e).message!!.contains("Query.unbound"), rootCause(e).message)
    }

    @Test
    fun `with eager init off the broken server starts and the binding fails on first use`() {
        val lazy = mapOf<String, Any>("micronaut.server.port" to -1, "micronaut.graphql.tools.eager-init" to false)
        ApplicationContext.run(EmbeddedServer::class.java, lazy, BROKEN_BINDING).use { server ->
            assertThrows(BeanInstantiationException::class.java) {
                server.applicationContext.getBean(GraphQLSchema::class.java)
            }
        }
    }

    private fun rootCause(e: Throwable): Throwable {
        var cause = e
        while (cause.cause != null && cause.cause !== cause) {
            cause = cause.cause!!
        }
        return cause
    }

}
