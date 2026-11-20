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
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton

@CompileStatic
@Factory
class PrecedenceFixtures {

    // A Relay-style interface with an id resolver next to an entity that has an id property
    static final String RELAY = 'precedence-relay'
    // A field that a property and a type resolver method can both bind
    static final String AMBIGUOUS = 'precedence-ambiguous'

    @Bean
    @Singleton
    @Requires(env = PrecedenceFixtures.RELAY)
    TypeDefinitionRegistryCustomizer relaySchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'interface Gizmo { id: ID! }\n' +
                            'type Sprocket implements Gizmo { id: ID! name: String! }\n' +
                            'extend type Query { gizmo: Gizmo }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

    // The example's mappings and the implementation of the interface
    @Bean
    @Singleton
    @Requires(env = PrecedenceFixtures.RELAY)
    @Replaces(value = SchemaMappingDictionaryCustomizer, factory = GraphQLFactory)
    SchemaMappingDictionaryCustomizer relayTypes() {
        return new SchemaMappingDictionaryCustomizer() {
            @Override
            void customize(SchemaMappingDictionary dictionary) {
                dictionary
                        .registerType('SecurityError', SecurityError)
                        .registerType('ValidationError', ValidationError)
                        .registerType('Sprocket', Sprocket)
            }
        }
    }

    @Bean
    @Singleton
    @Requires(env = PrecedenceFixtures.AMBIGUOUS)
    TypeDefinitionRegistryCustomizer ambiguousSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'type Gadget { name: String! label: String! }\n' +
                            'extend type Query { gadget: Gadget }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

}

interface Gizmo {
}

@CompileStatic
@GraphQLType
class Sprocket implements Gizmo {
    Long id = 7L
    String name = 'cog'
}

@CompileStatic
@Requires(env = PrecedenceFixtures.RELAY)
@Singleton
@GraphQLTypeResolver(Gizmo)
class GizmoResolver {

    @GraphQLSchemaCoordinate('Gizmo.id')
    String id(Gizmo gizmo) {
        return 'global'
    }

}

@CompileStatic
@Requires(env = PrecedenceFixtures.RELAY)
@Singleton
@GraphQLRootResolver
class RelayQuery {

    Gizmo gizmo() {
        return new Sprocket()
    }

}

@CompileStatic
@GraphQLType
class Gadget {
    String name = 'pen'
    String label = 'own'
}

@CompileStatic
@Requires(env = PrecedenceFixtures.AMBIGUOUS)
@Singleton
@GraphQLTypeResolver(Gadget)
class GadgetResolver {

    String label(Gadget gadget) {
        return 'resolved ' + gadget.name
    }

}

@CompileStatic
@Requires(env = PrecedenceFixtures.AMBIGUOUS)
@Singleton
@GraphQLRootResolver
class GadgetQuery {

    Gadget gadget() {
        return new Gadget()
    }

}
