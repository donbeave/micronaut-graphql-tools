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

import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping
import jakarta.inject.Singleton
import java.util.UUID

// The scalars of the module under their default names
const val SCALARS_DEFAULTS = "scalars-default"

// The same fields with a renamed scalar and a configured format
const val SCALARS_RENAMED = "scalars-renamed"

// The same fields with scalars enabled, renamed and disabled by properties, and the application's own UUID mapping
const val SCALARS_CONFIGURED = "scalars-configured"

// The description of the UUID scalar that the application registers itself
const val OWN_UUID = "The UUID of the application"

@Factory
class ScalarsFixtures {

    // tag::schema[]
    @Bean
    @Singleton
    @Requires(env = [SCALARS_DEFAULTS])
    fun defaultScalars() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "scalar LocalDate\n" +
                    "scalar OffsetDateTime\n" +
                    "scalar Duration\n" +
                    "scalar UUID\n" +
                    "scalar Url\n" +
                    "extend type Query {\n" +
                    "  bookedOn(date: LocalDate!): LocalDate!\n" +
                    "  bookedAt(time: OffsetDateTime!): OffsetDateTime!\n" +
                    "  elapsed(spent: Duration!): Duration!\n" +
                    "  payload(data: JSON): JSON\n" +
                    "  ticket(id: UUID!): UUID!\n" +
                    "  homepage(url: Url!): Url!\n" +
                    "}\n"
            )
        )
    }
    // end::schema[]

    @Bean
    @Singleton
    @Requires(env = [SCALARS_RENAMED])
    fun renamedScalars() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "scalar Date\n" +
                    "scalar OffsetDateTime\n" +
                    "scalar Duration\n" +
                    "scalar UUID\n" +
                    "scalar Url\n" +
                    "extend type Query {\n" +
                    "  bookedOn(date: Date!): Date!\n" +
                    "  bookedAt(time: OffsetDateTime!): OffsetDateTime!\n" +
                    "  elapsed(spent: Duration!): Duration!\n" +
                    "  payload(data: JSON): JSON\n" +
                    "  ticket(id: UUID!): UUID!\n" +
                    "  homepage(url: Url!): Url!\n" +
                    "}\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [SCALARS_CONFIGURED])
    fun configuredScalars() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "scalar Date\n" +
                    "scalar OffsetDateTime\n" +
                    "scalar Duration\n" +
                    "scalar UUID\n" +
                    "scalar Url\n" +
                    "extend type Query {\n" +
                    "  bookedOn(date: Date!): Date!\n" +
                    "  bookedAt(time: OffsetDateTime!): OffsetDateTime!\n" +
                    "  elapsed(spent: Duration!): Duration!\n" +
                    "  payload(data: JSON): JSON\n" +
                    "  ticket(id: UUID!): UUID!\n" +
                    "  homepage(url: Url!): Url!\n" +
                    "}\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [SCALARS_CONFIGURED])
    fun ownUuid(): GraphQLScalarMapping = GraphQLScalarMapping.of(
        GraphQLScalarType.newScalar(ExtendedScalars.UUID).description(OWN_UUID).build(),
        UUID::class.java
    )
}
