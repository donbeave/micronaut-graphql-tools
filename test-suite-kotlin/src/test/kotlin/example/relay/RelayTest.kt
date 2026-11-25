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
package example.relay

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): a Relay connection that is bound to own data classes, with strict mode
// at its default.
class RelayTest {

    private val page = "query (\$first: Int!, \$after: String) {" +
        " articles(first: \$first, after: \$after) {" +
        " totalCount edges { cursor node { id title } }" +
        " pageInfo { hasNextPage hasPreviousPage startCursor endCursor } } }"

    @Suppress("UNCHECKED_CAST")
    private fun page(context: ApplicationContext, first: Int, after: String?): Map<String, Any?> {
        val result = GraphQL.newGraphQL(context.getBean(GraphQLSchema::class.java)).build()
            .execute(ExecutionInput.newExecutionInput().query(page).variables(mapOf("first" to first, "after" to after)).build())
        assertTrue(result.errors.isEmpty(), result.errors.toString())
        return result.getData<Map<String, Any?>>()!!["articles"] as Map<String, Any?>
    }

    // tag::paging[]
    @Test
    @Suppress("UNCHECKED_CAST")
    fun `first and after page through the connection`() {
        ApplicationContext.run(mapOf(), RELAY).use { context ->
            val titles = mutableListOf<String>()
            var after: String? = null
            var pages = 0
            do {
                val connection = page(context, 2, after)
                assertEquals(5, connection["totalCount"])
                (connection["edges"] as List<Map<String, Any?>>).forEach {
                    titles.add((it["node"] as Map<String, Any?>)["title"] as String)
                }
                val pageInfo = connection["pageInfo"] as Map<String, Any?>
                assertEquals(pages > 0, pageInfo["hasPreviousPage"])
                after = pageInfo["endCursor"] as String?
                pages++
            } while (pageInfo["hasNextPage"] == true)
            assertEquals(3, pages)
            assertEquals(listOf("Article 1", "Article 2", "Article 3", "Article 4", "Article 5"), titles)
        }
    }
    // end::paging[]

    @Test
    fun `a negative first and a huge first are handled`() {
        ApplicationContext.run(mapOf(), RELAY).use { context ->
            val result = GraphQL.newGraphQL(context.getBean(GraphQLSchema::class.java)).build()
                .execute(ExecutionInput.newExecutionInput().query(page).variables(mapOf("first" to -1)).build())
            assertFalse(result.errors.isEmpty())
            assertEquals(5, (page(context, Int.MAX_VALUE, null)["edges"] as List<*>).size)
        }
    }

    @Test
    fun `an overflowing cursor is rejected`() {
        ApplicationContext.run(mapOf(), RELAY).use { context ->
            val cursor = java.util.Base64.getEncoder().encodeToString("offset:2147483647".toByteArray())
            val result = GraphQL.newGraphQL(context.getBean(GraphQLSchema::class.java)).build()
                .execute(ExecutionInput.newExecutionInput().query(page).variables(mapOf("first" to 2, "after" to cursor)).build())
            assertFalse(result.errors.isEmpty())
        }
    }

    @Test
    fun `a page after the last cursor is empty`() {
        ApplicationContext.run(mapOf(), RELAY).use { context ->
            val last = (page(context, 5, null)["pageInfo"] as Map<*, *>)["endCursor"] as String
            val connection = page(context, 2, last)
            assertEquals(listOf<Any>(), connection["edges"])
            val pageInfo = connection["pageInfo"] as Map<*, *>
            assertFalse(pageInfo["hasNextPage"] as Boolean)
            assertNull(pageInfo["startCursor"])
            assertNull(pageInfo["endCursor"])
        }
    }
}
