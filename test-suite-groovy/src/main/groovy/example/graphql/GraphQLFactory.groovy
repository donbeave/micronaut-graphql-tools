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
package example.graphql

import example.graphql.type.SecurityError
import example.graphql.type.ValidationError
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.core.io.ResourceResolver
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer
import jakarta.inject.Singleton

@CompileStatic
@Factory
class GraphQLFactory {

    // tag::registry[]
    @Bean
    @Singleton
    TypeDefinitionRegistry typeDefinitionRegistry(ResourceResolver resourceResolver) {
        InputStream inputStream = resourceResolver.getResourceAsStream('classpath:schema.graphqls').get()
        return new TypeDefinitionRegistry()
                .merge(new SchemaParser().parse(new BufferedReader(new InputStreamReader(inputStream))))
    }
    // end::registry[]

    @Bean
    @Singleton
    GraphQLSchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
        return new GraphQLSchemaMappingDictionaryCustomizer() {
            @Override
            void customize(SchemaMappingDictionary schemaMappingDictionary) {
                schemaMappingDictionary
                        .registerType('SecurityError', SecurityError)
                        .registerType('ValidationError', ValidationError)
            }
        }
    }
}
