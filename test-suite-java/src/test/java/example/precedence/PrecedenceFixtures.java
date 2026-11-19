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
package example.precedence;

import example.graphql.GraphQLFactory;
import example.graphql.type.SecurityError;
import example.graphql.type.ValidationError;
import graphql.schema.idl.SchemaParser;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer;
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer;
import jakarta.inject.Singleton;

@Factory
public class PrecedenceFixtures {

    // A Relay-style interface with an id resolver next to an entity that has getId()
    public static final String RELAY = "precedence-relay";
    // A field that a getter and a type resolver method can both bind
    public static final String AMBIGUOUS = "precedence-ambiguous";

    @Bean
    @Singleton
    @Requires(env = RELAY)
    TypeDefinitionRegistryCustomizer relaySchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "interface Gizmo { id: ID! }\n"
                        + "type Sprocket implements Gizmo { id: ID! name: String! }\n"
                        + "extend type Query { gizmo: Gizmo }\n"));
    }

    // The example's mappings and the implementation of the interface
    @Bean
    @Singleton
    @Requires(env = RELAY)
    @Replaces(value = SchemaMappingDictionaryCustomizer.class, factory = GraphQLFactory.class)
    SchemaMappingDictionaryCustomizer relayTypes() {
        return dictionary -> dictionary
                .registerType("SecurityError", SecurityError.class)
                .registerType("ValidationError", ValidationError.class)
                .registerType("Sprocket", Sprocket.class);
    }

    @Bean
    @Singleton
    @Requires(env = AMBIGUOUS)
    TypeDefinitionRegistryCustomizer ambiguousSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Gadget { name: String! label: String! }\n"
                        + "extend type Query { gadget: Gadget }\n"));
    }

}
