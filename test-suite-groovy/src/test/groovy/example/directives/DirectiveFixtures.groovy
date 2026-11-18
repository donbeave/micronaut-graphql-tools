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
package example.directives

import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.ResourceResolver
import io.micronaut.graphql.tools.SchemaDirective
import jakarta.inject.Singleton

@CompileStatic
@Factory
class DirectiveFixtures {

    static final String CONFLICT = 'directive-conflict'
    static final String UNWIRED = 'directive-unwired'
    static final String SCHEMA_UNLISTED = 'directive-schema-unlisted'

    @Bean
    @Singleton
    @Requires(env = DirectiveFixtures.CONFLICT)
    SchemaDirective upperForEveryElement() {
        SchemaDirective.everyElement(['upper'], new SchemaDirectiveWiring() { })
    }

    @Bean
    @Singleton
    @Requires(env = DirectiveFixtures.UNWIRED)
    @Replaces(TypeDefinitionRegistry)
    TypeDefinitionRegistry unwiredDirectiveRegistry(ResourceResolver resourceResolver) {
        registryWith(resourceResolver, 'directive @legacy on OBJECT\nextend type Book @legacy')
    }

    @Bean
    @Singleton
    @Requires(env = DirectiveFixtures.SCHEMA_UNLISTED)
    @Replaces(TypeDefinitionRegistry)
    TypeDefinitionRegistry schemaDirectiveRegistry(ResourceResolver resourceResolver) {
        registryWith(resourceResolver, 'directive @other on SCHEMA\nextend schema @other')
    }

    private static TypeDefinitionRegistry registryWith(ResourceResolver resourceResolver, String extension) {
        SchemaParser parser = new SchemaParser()
        new TypeDefinitionRegistry()
                .merge(parser.parse(resourceResolver.getResourceAsStream('classpath:schema.graphqls').orElseThrow().newReader()))
                .merge(parser.parse(extension))
    }
}
