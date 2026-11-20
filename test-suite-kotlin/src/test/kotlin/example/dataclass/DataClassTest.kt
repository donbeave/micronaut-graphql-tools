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
package example.dataclass

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest).
class DataClassTest {

    private fun run(query: String): Map<String, Any?> =
        ApplicationContext.run(mapOf<String, Any>(), DATA_CLASSES).use { context ->
            val schema = context.getBean(GraphQLSchema::class.java)
            val result = GraphQL.newGraphQL(schema).build().execute(query)
            assertTrue(result.errors.isEmpty(), result.errors.toString())
            result.getData<Map<String, Any?>>()!!
        }

    @Test
    fun `an output data class binds through its introspection`() {
        assertEquals(
            mapOf("account" to mapOf("name" to "Ada", "nickname" to null, "level" to 3, "tags" to emptyList<String>())),
            run("{ account { name nickname level tags } }")
        )
    }

    @Test
    fun `an omitted field takes the default argument`() {
        assertEquals(
            mapOf("describe" to "AccountInput(name=Ada, nickname=anonymous, level=1, tags=[new])"),
            run("{ describe(input: { name: \"Ada\" }) }")
        )
    }

    @Test
    fun `a field sent as null is null when the type is nullable`() {
        assertEquals(
            mapOf("describe" to "AccountInput(name=Ada, nickname=null, level=1, tags=[new])"),
            run("{ describe(input: { name: \"Ada\", nickname: null }) }")
        )
    }

    @Test
    fun `a present field wins over the default`() {
        assertEquals(
            mapOf("describe" to "AccountInput(name=Ada, nickname=Ally, level=7, tags=[a, b])"),
            run("{ describe(input: { name: \"Ada\", nickname: \"Ally\", level: 7, tags: [\"a\", \"b\"] }) }")
        )
    }

    @Test
    fun `a list of data classes applies the defaults to each element`() {
        assertEquals(
            mapOf(
                "describeList" to listOf(
                    "AccountInput(name=A, nickname=anonymous, level=1, tags=[new])",
                    "AccountInput(name=B, nickname=null, level=2, tags=[new])"
                )
            ),
            run("{ describeList(inputs: [{ name: \"A\" }, { name: \"B\", nickname: null, level: 2 }]) }")
        )
    }

    @Test
    fun `a nested data class applies its own defaults`() {
        assertEquals(
            mapOf("settings" to "SettingsInput(theme=light, owner=AccountInput(name=Ada, nickname=anonymous, level=1, tags=[new]))"),
            run("{ settings(input: { theme: \"light\", owner: { name: \"Ada\" } }) }")
        )
    }

    @Test
    fun `ArgumentValue tells omitted from null and a default argument on another field still applies`() {
        assertEquals(
            mapOf("a" to "omitted note=none", "b" to "null note=null", "c" to "present:x note=y"),
            run(
                "{ a: draft(input: { title: \"t\" }) b: draft(input: { title: \"t\", summary: null, note: null }) " +
                    "c: draft(input: { title: \"t\", summary: \"x\", note: \"y\" }) }"
            )
        )
    }

    @Test
    fun `a data class returned from an input keeps working with copy`() {
        assertEquals(
            mapOf("copyOf" to mapOf("name" to "Ada", "nickname" to "anonymous", "level" to 2, "tags" to listOf("new"))),
            run("{ copyOf(input: { name: \"Ada\" }) { name nickname level tags } }")
        )
    }
}
