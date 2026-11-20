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
package example.observability

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): the request timer and the spans of the observability module.
class ObservabilityTest {

    private fun execute(context: ApplicationContext, query: String, operationName: String?): ExecutionResult =
        context.getBean(GraphQL::class.java)
            .execute(ExecutionInput.newExecutionInput().query(query).operationName(operationName).build())

    // tag::metrics[]
    @Test
    fun aRequestHasATimerTaggedByOperationNameAndOutcome() {
        ApplicationContext.run(emptyMap<String, Any>(), OBSERVED).use { context ->
            val result = execute(context, "query Hello { observedHello }", "Hello")
            assertTrue(result.errors.isEmpty(), result.errors.toString())

            val timer = context.getBean(MeterRegistry::class.java).find("graphql.request")
                .tags("operation", "Hello", "outcome", "success").timer()
            assertNotNull(timer)
            assertEquals(1, timer!!.count())
        }
    }
    // end::metrics[]

    // tag::tracing[]
    @Test
    fun selectedFieldsGetSpansAndPropertyReadsDoNot() {
        val properties = mapOf(
            "micronaut.graphql.tools.observability.tracing.fields" to listOf("Query.observedHello", "ObservedBook.*")
        )
        ApplicationContext.run(properties, OBSERVED).use { context ->
            val result = execute(context, "query Both { observedHello observedBook { title } }", "Both")
            assertTrue(result.errors.isEmpty(), result.errors.toString())

            val spans = context.getBean(InMemorySpanExporter::class.java).finishedSpanItems
            assertEquals(listOf("Query.observedHello", "query Both"), spans.map { it.name }.sorted())
        }
    }
    // end::tracing[]

    @Test
    fun theCardinalityOfTheOperationTagIsCapped() {
        val properties = mapOf("micronaut.graphql.tools.observability.metrics.max-operations" to 2)
        ApplicationContext.run(properties, OBSERVED).use { context ->
            for (i in 0 until 10) {
                execute(context, "query Op$i { observedHello }", null)
            }
            val operations = context.getBean(MeterRegistry::class.java).find("graphql.request").timers()
                .map { it.id.getTag("operation") }.distinct().size
            assertEquals(3, operations)
        }
    }
}
