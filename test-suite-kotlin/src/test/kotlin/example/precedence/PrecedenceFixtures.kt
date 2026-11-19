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
package example.precedence

import example.graphql.GraphQLFactory
import example.graphql.type.SecurityError
import example.graphql.type.ValidationError
import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton

// A Relay-style interface with an id resolver next to an entity that has an id property
const val PRECEDENCE_RELAY = "precedence-relay"

// A field that a property and a type resolver method can both bind
const val PRECEDENCE_AMBIGUOUS = "precedence-ambiguous"

@Factory
class PrecedenceFixtures {

    @Bean
    @Singleton
    @Requires(env = [PRECEDENCE_RELAY])
    fun relaySchema() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "interface Gizmo { id: ID! }\n" +
                    "type Sprocket implements Gizmo { id: ID! name: String! }\n" +
                    "extend type Query { gizmo: Gizmo }\n"
            )
        )
    }

    // The example's mappings and the implementation of the interface
    @Bean
    @Singleton
    @Requires(env = [PRECEDENCE_RELAY])
    @Replaces(value = SchemaMappingDictionaryCustomizer::class, factory = GraphQLFactory::class)
    fun relayTypes() = SchemaMappingDictionaryCustomizer { dictionary ->
        dictionary
            .registerType("SecurityError", SecurityError::class.java)
            .registerType("ValidationError", ValidationError::class.java)
            .registerType("Sprocket", Sprocket::class.java)
    }

    @Bean
    @Singleton
    @Requires(env = [PRECEDENCE_AMBIGUOUS])
    fun ambiguousSchema() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Gadget { name: String! label: String! }\n" +
                    "extend type Query { gadget: Gadget }\n"
            )
        )
    }
}

interface Gizmo

@GraphQLType
class Sprocket : Gizmo {
    val id: Long = 7L
    val name: String = "cog"
}

@Requires(env = [PRECEDENCE_RELAY])
@GraphQLTypeResolver(Gizmo::class)
open class GizmoResolver {

    @GraphQLSchemaCoordinate("Gizmo.id")
    open fun id(gizmo: Gizmo): String = "global"
}

@Requires(env = [PRECEDENCE_RELAY])
@GraphQLRootResolver
open class RelayQuery {

    open fun gizmo(): Gizmo = Sprocket()
}

@GraphQLType
class Gadget {
    val name: String = "pen"
    val label: String = "own"
}

@Requires(env = [PRECEDENCE_AMBIGUOUS])
@GraphQLTypeResolver(Gadget::class)
open class GadgetResolver {

    open fun label(gadget: Gadget): String = "resolved " + gadget.name
}

@Requires(env = [PRECEDENCE_AMBIGUOUS])
@GraphQLRootResolver
open class GadgetQuery {

    open fun gadget(): Gadget = Gadget()
}
