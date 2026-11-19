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
import io.micronaut.graphql.tools.internal.discovery.SchemaDiscovery
import spock.lang.Specification

// The Groovy suite runs on the JVM; the schema index is the one the graphqlSchemaIndex task writes at build time.
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
