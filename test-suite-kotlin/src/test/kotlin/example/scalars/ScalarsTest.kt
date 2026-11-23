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
package example.scalars

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): the standard scalars of the scalars module, from the schema to the
// JSON of the response.
class ScalarsTest {

    private fun execute(context: ApplicationContext, query: String, variables: Map<String, Any> = emptyMap()): ExecutionResult {
        val schema = context.getBean(GraphQLSchema::class.java)
        return GraphQL.newGraphQL(schema).build()
            .execute(ExecutionInput.newExecutionInput().query(query).variables(variables).build())
    }

    private fun wire(context: ApplicationContext, result: ExecutionResult): String =
        context.getBean(JsonMapper::class.java).writeValueAsString(result.toSpecification())

    // tag::round-trip[]
    @Test
    fun everyScalarRoundTripsWithItsDefaultFormat() {
        ApplicationContext.run(emptyMap<String, Any>(), SCALARS_DEFAULTS).use { context ->
            val result = execute(
                context,
                "query (\$t: OffsetDateTime!, \$j: JSON) {" +
                    " bookedOn(date: \"2026-12-24\")" +
                    " bookedAt(time: \$t)" +
                    " elapsed(spent: \"PT1H30M\")" +
                    " payload(data: \$j)" +
                    " ticket(id: \"123e4567-e89b-12d3-a456-426614174000\")" +
                    " homepage(url: \"https://example.org/a?b=c\") }",
                mapOf("t" to "2026-12-24T10:15:30+01:00", "j" to mapOf("kind" to "x"))
            )
            assertTrue(result.errors.isEmpty(), result.errors.toString())
            assertEquals(
                "{\"data\":{\"bookedOn\":\"2026-12-25\",\"bookedAt\":\"2026-12-24T11:15:30+01:00\"," +
                    "\"elapsed\":\"PT2H\",\"payload\":{\"kind\":\"x\"}," +
                    "\"ticket\":\"123e4567-e89b-12d3-a456-426614174000\"," +
                    "\"homepage\":\"https://example.org/a?b=c\"}}",
                wire(context, result)
            )
        }
    }
    // end::round-trip[]

    // tag::renamed[]
    @Test
    fun aConfiguredNameAndFormatChangeTheTypeThatBinds() {
        val properties = mapOf(
            "micronaut.graphql.tools.scalars.local-date.name" to "Date",
            "micronaut.graphql.tools.scalars.local-date.format" to "dd/MM/yyyy"
        )
        ApplicationContext.run(properties, SCALARS_RENAMED).use { context ->
            assertTrue(context.getBean(GraphQLSchema::class.java).getType("Date") is GraphQLScalarType)
            assertNull(context.getBean(GraphQLSchema::class.java).getType("LocalDate"))
            val result = execute(context, "{ bookedOn(date: \"24/12/2026\") }")
            assertTrue(result.errors.isEmpty(), result.errors.toString())
            assertEquals(mapOf("bookedOn" to "25/12/2026"), result.getData<Any>())
        }
    }
    // end::renamed[]

    // tag::configured[]
    @Test
    fun aScalarCanBeEnabledRenamedAndDisabledByProperties() {
        val properties = mapOf(
            "micronaut.graphql.tools.scalars.offset-date-time.enabled" to true,
            "micronaut.graphql.tools.scalars.local-date.name" to "Date",
            "micronaut.graphql.tools.scalars.uuid.enabled" to false
        )
        ApplicationContext.run(properties, SCALARS_CONFIGURED).use { context ->
            val schema = context.getBean(GraphQLSchema::class.java)
            assertTrue(schema.getType("Date") is GraphQLScalarType)
            assertNull(schema.getType("LocalDate"))
            assertTrue(schema.getType("OffsetDateTime") is GraphQLScalarType)
            assertEquals(OWN_UUID, (schema.getType("UUID") as GraphQLScalarType).description)
            val result = execute(
                context,
                "{ bookedOn(date: \"2026-12-24\")" +
                    " bookedAt(time: \"2026-12-24T10:15:30+01:00\")" +
                    " ticket(id: \"123e4567-e89b-12d3-a456-426614174000\") }"
            )
            assertTrue(result.errors.isEmpty(), result.errors.toString())
            assertEquals(
                mapOf(
                    "bookedOn" to "2026-12-25",
                    "bookedAt" to "2026-12-24T11:15:30+01:00",
                    "ticket" to "123e4567-e89b-12d3-a456-426614174000"
                ),
                result.getData<Any>()
            )
        }
    }
    // end::configured[]

    @Test
    fun anUnknownScalarIdFailsTheStartup() {
        val properties = mapOf("micronaut.graphql.tools.scalars.local-dat.enabled" to false)
        val failure = assertThrows(Exception::class.java) {
            ApplicationContext.run(properties, SCALARS_DEFAULTS).close()
        }
        val messages = generateSequence<Throwable>(failure) { it.cause }.joinToString("\n") { it.message ?: "" }
        assertTrue(messages.contains("micronaut.graphql.tools.scalars.local-dat"), failure.toString())
        assertTrue(messages.contains("local-date"), failure.toString())
    }

    @Test
    fun aMisspelledPropertyOfAKnownScalarFailsTheStartup() {
        val properties = mapOf("micronaut.graphql.tools.scalars.local-date.nmae" to "Date")
        val failure = assertThrows(Exception::class.java) {
            ApplicationContext.run(properties, SCALARS_DEFAULTS).close()
        }
        val messages = generateSequence<Throwable>(failure) { it.cause }.joinToString("\n") { it.message ?: "" }
        assertTrue(messages.contains("micronaut.graphql.tools.scalars.local-date.nmae"), failure.toString())
        assertTrue(messages.contains("enabled, name, format"), failure.toString())
    }

    @Test
    fun anInvalidValueIsAnErrorThatNamesTheScalar() {
        ApplicationContext.run(emptyMap<String, Any>(), SCALARS_DEFAULTS).use { context ->
            val result = execute(context, "{ bookedOn(date: \"next week\") }")
            assertFalse(result.errors.isEmpty())
            assertTrue(result.errors[0].message.contains("LocalDate"), result.errors.toString())

            val variable = execute(context, "query (\$d: Duration!) { elapsed(spent: \$d) }", mapOf("d" to "90 minutes"))
            assertFalse(variable.errors.isEmpty())
            assertTrue(variable.errors[0].message.contains("Duration"), variable.errors.toString())
        }
    }

    @Test
    fun aJsonNumberThatCannotBeWrittenIsAnErrorThatNamesTheScalar() {
        ApplicationContext.run(emptyMap<String, Any>(), SCALARS_DEFAULTS).use { context ->
            val values = listOf<Any>(
                mapOf("a" to Double.NaN),
                mapOf("a" to listOf(java.math.BigDecimal("1e-10000"))),
                listOf(java.math.BigDecimal("1e10000")),
            )
            for (value in values) {
                val result = execute(context, "query (\$j: JSON) { payload(data: \$j) }", mapOf("j" to value))
                assertFalse(result.errors.isEmpty(), value.toString())
                assertTrue(result.errors[0].message.contains("JSON"), result.errors.toString())
            }
            val literal = execute(context, "{ payload(data: {a: 1e10000}) }")
            assertFalse(literal.errors.isEmpty())
            assertTrue(literal.errors[0].message.contains("JSON"), literal.errors.toString())
        }
    }
}
