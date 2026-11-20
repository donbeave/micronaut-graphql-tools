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

import graphql.schema.idl.SchemaParser
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import jakarta.inject.Singleton

@CompileStatic
@Factory
class ScalarsFixtures {

    // The scalars of the module under their default names
    static final String DEFAULTS = 'scalars-default'
    // The same fields with a renamed scalar and a configured format
    static final String RENAMED = 'scalars-renamed'

    // tag::schema[]
    @Bean
    @Singleton
    @Requires(env = ScalarsFixtures.DEFAULTS)
    TypeDefinitionRegistryCustomizer defaultScalars() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'scalar LocalDate\n' +
                            'scalar OffsetDateTime\n' +
                            'scalar Duration\n' +
                            'scalar JSON\n' +
                            'scalar UUID\n' +
                            'scalar Url\n' +
                            'extend type Query {\n' +
                            '  bookedOn(date: LocalDate!): LocalDate!\n' +
                            '  bookedAt(time: OffsetDateTime!): OffsetDateTime!\n' +
                            '  elapsed(spent: Duration!): Duration!\n' +
                            '  payload(data: JSON): JSON\n' +
                            '  ticket(id: UUID!): UUID!\n' +
                            '  homepage(url: Url!): Url!\n' +
                            '}\n'))
        } as TypeDefinitionRegistryCustomizer
    }
    // end::schema[]

    @Bean
    @Singleton
    @Requires(env = ScalarsFixtures.RENAMED)
    TypeDefinitionRegistryCustomizer renamedScalars() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'scalar Date\n' +
                            'scalar OffsetDateTime\n' +
                            'scalar Duration\n' +
                            'scalar JSON\n' +
                            'scalar UUID\n' +
                            'scalar Url\n' +
                            'extend type Query {\n' +
                            '  bookedOn(date: Date!): Date!\n' +
                            '  bookedAt(time: OffsetDateTime!): OffsetDateTime!\n' +
                            '  elapsed(spent: Duration!): Duration!\n' +
                            '  payload(data: JSON): JSON\n' +
                            '  ticket(id: UUID!): UUID!\n' +
                            '  homepage(url: Url!): Url!\n' +
                            '}\n'))
        } as TypeDefinitionRegistryCustomizer
    }

}
