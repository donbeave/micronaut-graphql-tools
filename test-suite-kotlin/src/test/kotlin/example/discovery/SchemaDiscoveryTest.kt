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
