package example.discovery

import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources
import io.micronaut.graphql.tools.schema.SchemaDiscovery
import spock.lang.Specification

// The Groovy suite runs on the JVM (D-38); the schema index is the one the graphqlSchemaIndex task writes at build time.
class SchemaDiscoverySpec extends Specification {

    static final List<String> LOCATIONS = ['classpath:discovery/**/*.graphqls']

    void "the index finds the schema files and the implicit roots apply"() {
        when:
        def loaded = SchemaDiscovery.discover(LOCATIONS, getClass().classLoader, true)
        def registry = loaded.registry()
        SchemaSources.addImplicitRoots(registry)

        then:
        loaded.read() == ['discovery/query.graphqls', 'discovery/types/library.graphqls']
        registry.getType('Library').isPresent()
        registry.schemaDefinition().get().operationTypeDefinitions[0].typeName.name == 'Query'
    }

    void "listing the classpath reads the same files as the index"() {
        expect:
        SchemaDiscovery.discover(LOCATIONS, getClass().classLoader, false).read() ==
                SchemaDiscovery.discover(LOCATIONS, getClass().classLoader, true).read()
    }
}
