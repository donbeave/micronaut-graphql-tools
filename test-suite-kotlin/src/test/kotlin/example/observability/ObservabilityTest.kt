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
import io.opentelemetry.api.common.AttributeKey
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
            "micronaut.graphql.tools.tracing.fields" to listOf("Query.observedHello", "ObservedBook.*")
        )
        ApplicationContext.run(properties, OBSERVED).use { context ->
            val result = execute(context, "query Both { observedHello observedBook { title } }", "Both")
            assertTrue(result.errors.isEmpty(), result.errors.toString())

            val spans = context.getBean(InMemorySpanExporter::class.java).finishedSpanItems
            assertEquals(listOf("Query.observedHello", "query"), spans.map { it.name }.sorted())
        }
    }
    // end::tracing[]

    // tag::span-name[]
    @Test
    fun theRequestSpanIsNamedAfterTheOperationTypeUnlessTheKeyAddsTheName() {
        ApplicationContext.run(emptyMap<String, Any>(), OBSERVED).use { context ->
            execute(context, "query Hello { observedHello }", "Hello")
            assertEquals(listOf("query"), names(context))
        }
        val properties = mapOf("micronaut.graphql.tools.tracing.add-operation-name-to-span-name" to true)
        ApplicationContext.run(properties, OBSERVED).use { context ->
            execute(context, "query Hello { observedHello }", "Hello")
            assertEquals(listOf("query Hello"), names(context))
        }
    }
    // end::span-name[]

    // tag::new-span[]
    @Test
    fun aNewSpanResolverIsAChildOfTheRequestSpan() {
        ApplicationContext.run(emptyMap<String, Any>(), OBSERVED).use { context ->
            val result = execute(context, "query Work { observedTraced observedTagged(value: \"x\") }", "Work")
            assertTrue(result.errors.isEmpty(), result.errors.toString())

            val spans = context.getBean(InMemorySpanExporter::class.java).finishedSpanItems
            val request = spans.first { it.name == "query" }
            val work = spans.first { it.name.endsWith("#observed.work") }
            val tagged = spans.first { it.name.endsWith("#observed.tagged") }
            assertEquals(request.spanId, work.parentSpanId)
            assertEquals("x", tagged.attributes.get(AttributeKey.stringKey("observed.value")))
        }
    }
    // end::new-span[]

    // tag::timed-resolver[]
    @Test
    fun aTimedResolverHasATimer() {
        ApplicationContext.run(emptyMap<String, Any>(), OBSERVED).use { context ->
            val result = execute(context, "query Work { observedTimed }", "Work")
            assertTrue(result.errors.isEmpty(), result.errors.toString())

            val timer = context.getBean(MeterRegistry::class.java).find("observed.timed").timer()
            assertNotNull(timer)
            assertEquals(1, timer!!.count())
        }
    }
    // end::timed-resolver[]

    private fun names(context: ApplicationContext): List<String> =
        context.getBean(InMemorySpanExporter::class.java).finishedSpanItems.map { it.name }

    @Test
    fun theCardinalityOfTheOperationTagIsCapped() {
        val properties = mapOf("micronaut.metrics.binders.graphql.max-operations" to 2)
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
