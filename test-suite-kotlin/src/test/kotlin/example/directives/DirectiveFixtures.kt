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
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.ResourceResolver
import io.micronaut.graphql.tools.SchemaDirective
import jakarta.inject.Singleton

@Factory
class DirectiveFixtures {

    @Bean
    @Singleton
    @Requires(env = [CONFLICT])
    fun upperForEveryElement(): SchemaDirective =
        SchemaDirective.everyElement(listOf("upper"), object : SchemaDirectiveWiring {})

    @Bean
    @Singleton
    @Requires(env = [UNWIRED])
    @Replaces(TypeDefinitionRegistry::class)
    fun unwiredDirectiveRegistry(resourceResolver: ResourceResolver): TypeDefinitionRegistry =
        registryWith(resourceResolver, "directive @legacy on OBJECT\nextend type Book @legacy")

    @Bean
    @Singleton
    @Requires(env = [SCHEMA_UNLISTED])
    @Replaces(TypeDefinitionRegistry::class)
    fun schemaDirectiveRegistry(resourceResolver: ResourceResolver): TypeDefinitionRegistry =
        registryWith(resourceResolver, "directive @other on SCHEMA\nextend schema @other")

    private fun registryWith(resourceResolver: ResourceResolver, extension: String): TypeDefinitionRegistry {
        val parser = SchemaParser()
        val schema = resourceResolver.getResourceAsStream("classpath:schema.graphqls").orElseThrow().bufferedReader()
        return TypeDefinitionRegistry().merge(parser.parse(schema)).merge(parser.parse(extension))
    }

    companion object {
        const val CONFLICT = "directive-conflict"
        const val UNWIRED = "directive-unwired"
        const val SCHEMA_UNLISTED = "directive-schema-unlisted"
    }
}
