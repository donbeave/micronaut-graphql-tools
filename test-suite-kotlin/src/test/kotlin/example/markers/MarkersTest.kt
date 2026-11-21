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
package example.markers

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts
import io.micronaut.graphql.tools.resolver.TypeResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): resolvers declared through marker interfaces bind with no
// reflective annotation read.
class MarkersTest {

    private fun run(context: ApplicationContext, query: String): Any? {
        val schema = context.getBean(GraphQLSchema::class.java)
        val result = GraphQL.newGraphQL(schema).build().execute(query)
        assertTrue(result.errors.isEmpty(), result.errors.toString())
        return result.getData<Any>()
    }

    @Test
    fun `marker contracts bind a generic type resolver and the operation of the marker`() {
        ApplicationContext.run(mapOf(), MARKERS).use { context ->
            assertEquals(
                mapOf("shelf" to mapOf("id" to "1", "slots" to 12, "label" to "Shelf 1"), "stock" to "queried"),
                run(context, "{ shelf { id slots label } stock }")
            )
            assertEquals(mapOf("stock" to "mutated"), run(context, "mutation { stock }"))
        }
    }

    @Test
    fun `an implementation without a scope annotation is not a bean`() {
        ApplicationContext.run(mapOf(), MARKERS).use { context ->
            assertFalse(context.containsBean(UnscopedShelfQuery::class.java))
            assertTrue(context.containsBean(ShelfQuery::class.java))
        }
    }

    private fun contracts(context: ApplicationContext, type: Class<*>) =
        context.getBeanDefinition(type).annotationMetadata
            .findAnnotation<Annotation>(ResolverContracts::class.java.name).get()

    @Test
    fun `the visitor records the markers in the annotation metadata`() {
        ApplicationContext.run(mapOf(), MARKERS).use { context ->
            val query = contracts(context, ShelfQuery::class.java)
            val labels = contracts(context, ShelfLabels::class.java)
            val mutation = contracts(context, StockMutation::class.java)

            assertEquals(listOf("query"), query.stringValues("operations").toList())
            assertEquals(listOf("query||shelf/0"), query.stringValues("contractMethods").toList())
            assertEquals(listOf(Shelf::class.java.name), labels.annotationClassValues("value").map { it.name })
            assertEquals(listOf(TypeResolver::class.java.name), labels.stringValues("declaredBy").toList())
            assertEquals(listOf("mutation"), mutation.stringValues("operations").toList())
        }
    }
}
