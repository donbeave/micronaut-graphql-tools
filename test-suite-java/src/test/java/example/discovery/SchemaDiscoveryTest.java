package example.discovery;

import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources;
import io.micronaut.graphql.tools.schema.SchemaDiscovery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): the schema files are found from the index
// META-INF/micronaut-graphql-tools/schema-index.txt, which the graphqlSchemaIndex task writes at build time.
class SchemaDiscoveryTest {

    private static final List<String> LOCATIONS = List.of("classpath:discovery/**/*.graphqls");

    @Test
    void theIndexFindsTheSchemaFilesAndTheImplicitRootsApply() {
        SchemaSources.Loaded loaded = SchemaDiscovery.discover(LOCATIONS, getClass().getClassLoader(), true);

        assertEquals(List.of("discovery/query.graphqls", "discovery/types/library.graphqls"), loaded.read());
        TypeDefinitionRegistry registry = loaded.registry();
        SchemaSources.addImplicitRoots(registry);
        assertTrue(registry.getType("Library").isPresent());
        assertEquals("Query", registry.schemaDefinition().orElseThrow().getOperationTypeDefinitions().get(0)
                .getTypeName().getName());
    }

    @Test
    void theDefaultStrategyReadsTheSameFilesWhateverTheRuntime() {
        assertEquals(SchemaDiscovery.discover(LOCATIONS, getClass().getClassLoader(), true).read(),
                SchemaDiscovery.discover(LOCATIONS, getClass().getClassLoader()).read());
    }

}
