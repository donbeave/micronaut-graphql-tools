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
package example.discovery

import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources
import io.micronaut.graphql.tools.schema.SchemaDiscovery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): the schema files are found from the index
// META-INF/micronaut-graphql-tools/schema-index.txt, which the graphqlSchemaIndex task writes at build time.
class SchemaDiscoveryTest {

    private val locations = listOf("classpath:discovery/**/*.graphqls")

    @Test
    fun `the index finds the schema files and the implicit roots apply`() {
        val loaded = SchemaDiscovery.discover(locations, javaClass.classLoader, true)

        assertEquals(listOf("discovery/query.graphqls", "discovery/types/library.graphqls"), loaded.read())
        val registry = loaded.registry()
        SchemaSources.addImplicitRoots(registry)
        assertTrue(registry.getType("Library").isPresent)
        assertEquals("Query", registry.schemaDefinition().orElseThrow().operationTypeDefinitions[0].typeName.name)
    }

    @Test
    fun `the default strategy reads the same files whatever the runtime`() {
        assertEquals(
            SchemaDiscovery.discover(locations, javaClass.classLoader, true).read(),
            SchemaDiscovery.discover(locations, javaClass.classLoader).read(),
        )
    }
}
